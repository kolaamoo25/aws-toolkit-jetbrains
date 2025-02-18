// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.rds.auth

import com.intellij.database.Dbms
import com.intellij.database.dataSource.DatabaseConnectionInterceptor.ProtoConnection
import com.intellij.database.dataSource.DatabaseConnectionPoint
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.testFramework.ProjectRule
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.aws.toolkits.core.region.AwsRegion
import software.aws.toolkits.core.utils.RuleUtils
import software.aws.toolkits.core.utils.unwrap
import software.aws.toolkits.jetbrains.core.credentials.MockCredentialManagerRule
import software.aws.toolkits.jetbrains.core.region.MockRegionProviderRule
import software.aws.toolkits.jetbrains.datagrip.CREDENTIAL_ID_PROPERTY
import software.aws.toolkits.jetbrains.datagrip.REGION_ID_PROPERTY
import software.aws.toolkits.jetbrains.datagrip.RequireSsl
import software.aws.toolkits.resources.message

class IamAuthTest {
    @Rule
    @JvmField
    val projectRule = ProjectRule()

    private val iamAuth = IamAuth()
    private val credentialId = RuleUtils.randomName()
    private val defaultRegion = RuleUtils.randomName()
    private val username = RuleUtils.randomName()
    private val instancePort = RuleUtils.randomNumber().toString()
    private val dbHost = "${RuleUtils.randomName()}.555555.us-west-2.rds.amazonaws.com"
    private val connectionPort = 5432

    private val mockCreds = AwsBasicCredentials.create("Access", "ItsASecret")

    @Rule
    @JvmField
    val regionProvider = MockRegionProviderRule()

    @Rule
    @JvmField
    val credentialManager = MockCredentialManagerRule()

    @Before
    fun setUp() {
        credentialManager.addCredentials(credentialId, mockCreds)
        regionProvider.addRegion(AwsRegion(defaultRegion, RuleUtils.randomName(), RuleUtils.randomName()))
    }

    @Test
    fun `Intercept credentials succeeds`() {
        val connection = iamAuth.intercept(buildConnection(), false)?.unwrap()
        assertThat(connection).isNotNull
        assertThat(connection!!.connectionProperties).containsKey("user")
        assertThat(connection.connectionProperties["user"]).isEqualTo(username)
        assertThat(connection.connectionProperties).containsKey("password")
        assertThat(connection.connectionProperties["password"])
            .contains("X-Amz-Signature")
            .contains("connect")
            .contains(username)
            .contains(instancePort)
            .doesNotStartWith("https://")
    }

    @Test
    fun `Intercept credentials fails no port`() {
        assertThatThrownBy { iamAuth.intercept(buildConnection(hasPort = false, hasBadHost = true), false)?.unwrap() }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage(message("rds.validation.no_instance_port"))
    }

    @Test
    fun `Intercept credentials fails no host`() {
        assertThatThrownBy { iamAuth.intercept(buildConnection(hasHost = false, hasBadHost = true), false)?.unwrap() }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage(message("rds.validation.no_instance_host"))
    }

    @Test
    fun `Valid connection`() {
        val authInformation = iamAuth.getAuthInformation(buildConnection())
        assertThat(authInformation.port.toString()).isEqualTo(instancePort)
        assertThat(authInformation.user).isEqualTo(username)
        assertThat(authInformation.connectionSettings.region.id).isEqualTo(defaultRegion)
        assertThat(authInformation.address).isEqualTo(dbHost)
    }

    @Test
    fun `No username`() {
        assertThatThrownBy { iamAuth.getAuthInformation(buildConnection(hasUsername = false)) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage(message("rds.validation.username"))
    }

    @Test
    fun `No region`() {
        assertThatThrownBy { iamAuth.getAuthInformation(buildConnection(hasRegion = false)) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage(message("settings.regions.none_selected"))
    }

    @Test
    fun `No credentials`() {
        assertThatThrownBy { iamAuth.getAuthInformation(buildConnection(hasCredentials = false)) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage(message("settings.credentials.none_selected"))
    }

    @Test
    fun `Valid mysql aurora connection`() {
        val authInformation = iamAuth.getAuthInformation(buildConnection(dbmsType = Dbms.MYSQL_AURORA))
        assertThat(authInformation.port.toString()).isEqualTo(instancePort)
        assertThat(authInformation.user).isEqualTo(username)
        assertThat(authInformation.connectionSettings.region.id).isEqualTo(defaultRegion)
        assertThat(authInformation.address).isEqualTo(dbHost)
    }

    @Test
    fun `No ssl config aurora mysql throws`() {
        assertThatThrownBy { iamAuth.getAuthInformation(buildConnection(dbmsType = Dbms.MYSQL_AURORA, hasSslConfig = false)) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage(message("rds.validation.aurora_mysql_ssl_required"))
    }

    @Test
    fun `Generate pre-signed auth token request succeeds`() {
        val connection = iamAuth.getAuthInformation(buildConnection())
        val request = iamAuth.generateAuthToken(connection)
        assertThat(request)
            .contains("X-Amz-Signature")
            .contains("connect")
            .contains(username)
            .contains(dbHost)
            .contains(instancePort)
            .doesNotStartWith("https://")
    }

    @Test
    fun `Generate pre-signed auth token request succeeds using default host and port`() {
        val connection = iamAuth.getAuthInformation(buildConnection(hasHost = false, hasPort = false))
        val request = iamAuth.generateAuthToken(connection)
        assertThat(request)
            .contains("X-Amz-Signature")
            .contains("connect")
            .contains(username)
            .contains(dbHost)
            .contains(connectionPort.toString())
            .doesNotStartWith("https://")
    }

    private fun buildConnection(
        hasUsername: Boolean = true,
        hasRegion: Boolean = true,
        hasHost: Boolean = true,
        hasPort: Boolean = true,
        hasCredentials: Boolean = true,
        hasBadHost: Boolean = false,
        hasSslConfig: Boolean = true,
        dbmsType: Dbms = Dbms.POSTGRES
    ): ProtoConnection {
        val mockConnection = mock<LocalDataSource> {
            on { url } doReturn "jdbc:postgresql://$dbHost:$connectionPort/dev"
            on { databaseDriver } doReturn null
            on { driverClass } doReturn "org.postgresql.Driver"
            on { username } doReturn if (hasUsername) username else ""
            on { dbms } doReturn dbmsType
            on { sslCfg } doReturn if (hasSslConfig) RequireSsl else null
        }
        val dbConnectionPoint = mock<DatabaseConnectionPoint> {
            on { url } doAnswer { if (hasBadHost) null else "jdbc:postgresql://$dbHost:$connectionPort/dev" }
            on { additionalProperties } doAnswer {
                val m = mutableMapOf<String, String>()
                if (hasCredentials) {
                    m[CREDENTIAL_ID_PROPERTY] = credentialId
                }
                if (hasRegion) {
                    m[REGION_ID_PROPERTY] = defaultRegion
                }
                if (hasHost) {
                    m[RDS_SIGNING_HOST_PROPERTY] = dbHost
                }
                if (hasPort) {
                    m[RDS_SIGNING_PORT_PROPERTY] = instancePort
                }
                m
            }
            on { dataSource } doReturn mockConnection
            on { databaseDriver } doAnswer {
                mock {
                    on { id } doReturn "id"
                }
            }
        }
        return mock {
            val m = mutableMapOf<String, String>()
            on { connectionPoint } doReturn dbConnectionPoint
            on { runConfiguration } doAnswer {
                mock {
                    on { project } doAnswer { projectRule.project }
                }
            }
            on { connectionProperties } doReturn m
        }
    }
}
