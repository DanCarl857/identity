/*
 * Copyright 2017 The Mifos Initiative.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.mifos.identity.internal.command.handler;

import com.google.gson.Gson;
import io.mifos.anubis.token.TenantAccessTokenSerializer;
import io.mifos.anubis.token.TokenSerializationResult;
import io.mifos.core.lang.ApplicationName;
import io.mifos.core.lang.DateConverter;
import io.mifos.core.lang.security.RsaKeyPairFactory;
import io.mifos.identity.internal.command.AuthenticationCommandResponse;
import io.mifos.identity.internal.command.PasswordAuthenticationCommand;
import io.mifos.identity.internal.command.RefreshTokenAuthenticationCommand;
import io.mifos.identity.internal.repository.*;
import io.mifos.tool.crypto.HashGenerator;
import io.mifos.tool.crypto.SaltGenerator;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.springframework.jms.core.JmsTemplate;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.when;

/**
 * @author Myrle Krantz
 */
public class AuthenticationCommandHandlerTest {
  private static final String USER_NAME = "me";
  private static final String ROLE = "I";
  private static final String PASSWORD = "mine";
  private static final int ITERATION_COUNT = 20;
  private static final String TEST_APPLICATION_NAME = "test-v1";
  private static final long ACCESS_TOKEN_TIME_TO_LIVE = 20;
  private static final long REFRESH_TOKEN_TIME_TO_LIVE = 40;
  private static final int GRACE_PERIOD = 2;
  private static AuthenticationCommandHandler commandHandler;

  @BeforeClass()
  static public void setup()
  {
    RsaKeyPairFactory.KeyPairHolder keyPair = RsaKeyPairFactory.createKeyPair();

    final Users users = Mockito.mock(Users.class);
    final Roles roles = Mockito.mock(Roles.class);
    final PermittableGroups permittableGroups = Mockito.mock(PermittableGroups.class);
    final Signatures signatures = Mockito.mock(Signatures.class);
    final Tenants tenants = Mockito.mock(Tenants.class);
    final HashGenerator hashGenerator = Mockito.mock(HashGenerator.class);
    final TenantAccessTokenSerializer tenantAccessTokenSerializer
        = Mockito.mock(TenantAccessTokenSerializer.class);
    final TenantRefreshTokenSerializer tenantRefreshTokenSerializer
        = Mockito.mock(TenantRefreshTokenSerializer.class);
    final JmsTemplate jmsTemplate = Mockito.mock(JmsTemplate.class);
    final ApplicationName applicationName = Mockito.mock(ApplicationName.class);
    final Gson gson = new Gson();
    final Logger logger = Mockito.mock(Logger.class);

    commandHandler = new AuthenticationCommandHandler(
        users, roles, permittableGroups, signatures, tenants,
        hashGenerator,
        tenantAccessTokenSerializer, tenantRefreshTokenSerializer,
        jmsTemplate, applicationName,
        gson, logger);

    final PrivateTenantInfoEntity privateTenantInfoEntity = new PrivateTenantInfoEntity();
    privateTenantInfoEntity.setFixedSalt(ByteBuffer.wrap(new SaltGenerator().createRandomSalt()));
    privateTenantInfoEntity.setTimeToChangePasswordAfterExpirationInDays(GRACE_PERIOD);
    when(tenants.getPrivateTenantInfo()).thenReturn(Optional.of(privateTenantInfoEntity));

    final PrivateSignatureEntity privateSignatureEntity = new PrivateSignatureEntity();
    privateSignatureEntity.setKeyTimestamp(keyPair.getTimestamp());
    privateSignatureEntity.setPrivateKeyExp(keyPair.getPrivateKeyExp());
    privateSignatureEntity.setPrivateKeyMod(keyPair.getPrivateKeyMod());
    when(signatures.getPrivateSignature()).thenReturn(Optional.of(privateSignatureEntity));

    final UserEntity userEntity = new UserEntity();
    userEntity.setRole(ROLE);
    userEntity.setIdentifier(USER_NAME);
    userEntity.setIterationCount(ITERATION_COUNT);
    userEntity.setPassword(ByteBuffer.wrap(PASSWORD.getBytes()));
    userEntity.setSalt(ByteBuffer.wrap(new SaltGenerator().createRandomSalt()));
    userEntity.setPasswordExpiresOn(dataStaxNow());

    when(users.get(USER_NAME)).thenReturn(Optional.of(userEntity));

    final List<PermissionType> permissionsList = new ArrayList<>();
    final RoleEntity roleEntity = new RoleEntity(ROLE, permissionsList);
    when(roles.get(ROLE)).thenReturn(Optional.of(roleEntity));

    when(applicationName.toString()).thenReturn(TEST_APPLICATION_NAME);

    final TokenSerializationResult accessTokenSerializationResult = new TokenSerializationResult("blah", LocalDateTime.now(ZoneId.of("UTC")).plusSeconds(ACCESS_TOKEN_TIME_TO_LIVE));
    when(tenantAccessTokenSerializer.build(anyObject())).thenReturn(accessTokenSerializationResult);

    final TokenSerializationResult refreshTokenSerializationResult = new TokenSerializationResult("blah", LocalDateTime.now(ZoneId.of("UTC")).plusSeconds(REFRESH_TOKEN_TIME_TO_LIVE));
    when(tenantRefreshTokenSerializer.build(anyObject())).thenReturn(refreshTokenSerializationResult);

    final TenantRefreshTokenSerializer.Deserialized deserialized = new TenantRefreshTokenSerializer.Deserialized(USER_NAME, Date.from(Instant.now().plusSeconds(REFRESH_TOKEN_TIME_TO_LIVE)), TEST_APPLICATION_NAME);
    when(tenantRefreshTokenSerializer.deserialize(anyObject())).thenReturn(deserialized);

    when(hashGenerator.isEqual(any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(true);
  }

  private static com.datastax.driver.core.LocalDate dataStaxNow() {

    return com.datastax.driver.core.LocalDate.fromDaysSinceEpoch((int) LocalDate.now(ZoneId.of("UTC")).toEpochDay());
  }

  @Test
  public void correctPasswordAuthentication()
  {
    final PasswordAuthenticationCommand command = new PasswordAuthenticationCommand(USER_NAME, PASSWORD);

    final AuthenticationCommandResponse commandResponse = commandHandler.process(command);
    Assert.assertNotNull(commandResponse);
  }

  @Test
  public void correctRefreshTokenAuthentication()
  {
    final String refreshTokenPlaceHolder = "refresh_token";
    final RefreshTokenAuthenticationCommand command = new RefreshTokenAuthenticationCommand(refreshTokenPlaceHolder);

    final LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));

    final AuthenticationCommandResponse commandResponse = commandHandler.process(command);
    Assert.assertNotNull(commandResponse);
    Assert.assertNotNull(commandResponse.getRefreshToken());
    Assert.assertEquals(commandResponse.getRefreshToken(), refreshTokenPlaceHolder);

    checkExpiration(commandResponse.getAccessTokenExpiration(), now, ACCESS_TOKEN_TIME_TO_LIVE);
    checkExpiration(commandResponse.getRefreshTokenExpiration(), now, REFRESH_TOKEN_TIME_TO_LIVE);
  }

  private void checkExpiration(final String expirationString, final LocalDateTime now, final long timeToLive) {
    final LocalDateTime expectedExpiration = now.plusSeconds(timeToLive);
    final LocalDateTime parsedExpiration = LocalDateTime.parse(expirationString, DateTimeFormatter.ISO_DATE_TIME);

    final long deltaFromExpected = Math.abs(parsedExpiration.until(expectedExpiration, ChronoUnit.SECONDS));

    Assert.assertTrue("Delta from expected should have been less than 2 second, but was " + deltaFromExpected +
                    ". Expiration string was " + expirationString + ".  Now was " + now + ".",
            deltaFromExpected <= 2);
  }

  @Test
  public void correctDeterminationOfPasswordExpiration()
  {
    final LocalDate passwordExpirationFromToday = LocalDate.now(ZoneId.of("UTC"));
    Assert.assertTrue(AuthenticationCommandHandler.pastExpiration(passwordExpirationFromToday));

    final LocalDate passwordExpirationFromYesterday = passwordExpirationFromToday.minusDays(1);
    Assert.assertTrue(AuthenticationCommandHandler.pastExpiration(passwordExpirationFromYesterday));

    final LocalDate passwordExpirationFromTommorrow = passwordExpirationFromToday.plusDays(1);
    Assert.assertFalse(AuthenticationCommandHandler.pastExpiration(passwordExpirationFromTommorrow));

  }

  @Test
  public void correctDeterminationOfPasswordGracePeriod()
  {
    final LocalDate passwordExpirationFromToday = LocalDate.now(ZoneId.of("UTC"));
    Assert.assertFalse(AuthenticationCommandHandler.pastGracePeriod(passwordExpirationFromToday, GRACE_PERIOD));

    final LocalDate nowJustWithinPasswordExpirationAndGracePeriod = passwordExpirationFromToday.minusDays(GRACE_PERIOD - 1);
    Assert.assertFalse(AuthenticationCommandHandler.pastGracePeriod(nowJustWithinPasswordExpirationAndGracePeriod, GRACE_PERIOD));

    final LocalDate nowJustOutsideOfPasswordExpirationAndGracePeriod = passwordExpirationFromToday.minusDays(GRACE_PERIOD);
    Assert.assertTrue(AuthenticationCommandHandler.pastGracePeriod(nowJustOutsideOfPasswordExpirationAndGracePeriod, GRACE_PERIOD));
  }

  @Test
  public void matchingFormatOfDates() {
    final Instant now = Instant.now();
    final Date nowDate = Date.from(now);
    final LocalDateTime nowLocalDateTime = LocalDateTime.ofInstant(now, ZoneId.of("UTC"));
    final LocalDate nowLocalDate = nowLocalDateTime.toLocalDate();

    final String dateString = DateConverter.toIsoString(nowDate);
    final String localDateTimeString = DateConverter.toIsoString(nowLocalDateTime);
    final String localDateString = DateConverter.toIsoString(nowLocalDate);

    Assert.assertEquals(dateString, localDateTimeString);
    Assert.assertTrue(localDateTimeString.startsWith(localDateString.substring(0, localDateString.length()-1))); //(removing Z)
  }
}
