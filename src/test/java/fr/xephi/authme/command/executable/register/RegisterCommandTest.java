package fr.xephi.authme.command.executable.register;

import fr.xephi.authme.TestHelper;
import fr.xephi.authme.mail.SendMailSSL;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.process.Management;
import fr.xephi.authme.security.HashAlgorithm;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.service.ValidationService;
import fr.xephi.authme.settings.properties.EmailSettings;
import fr.xephi.authme.settings.properties.RegistrationSettings;
import fr.xephi.authme.settings.properties.RegistrationArgumentType;
import fr.xephi.authme.settings.properties.SecuritySettings;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;

import static fr.xephi.authme.AuthMeMatchers.stringWithLength;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.hamcrest.MockitoHamcrest.argThat;

/**
 * Test for {@link RegisterCommand}.
 */
@RunWith(MockitoJUnitRunner.class)
public class RegisterCommandTest {

    @InjectMocks
    private RegisterCommand command;

    @Mock
    private CommonService commandService;

    @Mock
    private Management management;

    @Mock
    private SendMailSSL sendMailSsl;

    @Mock
    private ValidationService validationService;

    @BeforeClass
    public static void setup() {
        TestHelper.setupLogger();
    }

    @Before
    public void linkMocksAndProvideSettingDefaults() {
        given(commandService.getProperty(SecuritySettings.PASSWORD_HASH)).willReturn(HashAlgorithm.BCRYPT);
        given(commandService.getProperty(RegistrationSettings.REGISTRATION_TYPE)).willReturn(RegistrationArgumentType.PASSWORD);
    }

    @Test
    public void shouldNotRunForNonPlayerSender() {
        // given
        CommandSender sender = mock(BlockCommandSender.class);

        // when
        command.executeCommand(sender, Collections.emptyList());

        // then
        verify(sender).sendMessage(argThat(containsString("Player only!")));
        verifyZeroInteractions(management, sendMailSsl);
    }

    @Test
    public void shouldForwardToManagementForTwoFactor() {
        // given
        given(commandService.getProperty(SecuritySettings.PASSWORD_HASH)).willReturn(HashAlgorithm.TWO_FACTOR);
        Player player = mock(Player.class);

        // when
        command.executeCommand(player, Collections.emptyList());

        // then
        verify(management).performRegister(player, "", "", true);
        verifyZeroInteractions(sendMailSsl);
    }

    @Test
    public void shouldReturnErrorForEmptyArguments() {
        // given
        Player player = mock(Player.class);

        // when
        command.executeCommand(player, Collections.emptyList());

        // then
        verify(commandService).send(player, MessageKey.USAGE_REGISTER);
        verifyZeroInteractions(management, sendMailSsl);
    }

    @Test
    public void shouldReturnErrorForMissingConfirmation() {
        // given
        given(commandService.getProperty(RegistrationSettings.REGISTRATION_TYPE)).willReturn(RegistrationArgumentType.PASSWORD_WITH_CONFIRMATION);
        Player player = mock(Player.class);

        // when
        command.executeCommand(player, Collections.singletonList("arrrr"));

        // then
        verify(commandService).send(player, MessageKey.USAGE_REGISTER);
        verifyZeroInteractions(management, sendMailSsl);
    }

    @Test
    public void shouldReturnErrorForMissingEmailConfirmation() {
        // given
        given(commandService.getProperty(RegistrationSettings.REGISTRATION_TYPE)).willReturn(RegistrationArgumentType.EMAIL_WITH_CONFIRMATION);
        Player player = mock(Player.class);

        // when
        command.executeCommand(player, Collections.singletonList("test@example.org"));

        // then
        verify(commandService).send(player, MessageKey.USAGE_REGISTER);
        verifyZeroInteractions(management, sendMailSsl);
    }

    @Test
    public void shouldThrowErrorForMissingEmailConfiguration() {
        // given
        given(commandService.getProperty(RegistrationSettings.REGISTRATION_TYPE)).willReturn(RegistrationArgumentType.EMAIL);
        given(sendMailSsl.hasAllInformation()).willReturn(false);
        Player player = mock(Player.class);

        // when
        command.executeCommand(player, Collections.singletonList("myMail@example.tld"));

        // then
        verify(commandService).send(player, MessageKey.INCOMPLETE_EMAIL_SETTINGS);
        verify(sendMailSsl).hasAllInformation();
        verifyZeroInteractions(management);
    }

    @Test
    public void shouldRejectInvalidEmail() {
        // given
        String playerMail = "player@example.org";
        given(validationService.validateEmail(playerMail)).willReturn(false);
        given(commandService.getProperty(RegistrationSettings.REGISTRATION_TYPE)).willReturn(RegistrationArgumentType.EMAIL_WITH_CONFIRMATION);
        given(sendMailSsl.hasAllInformation()).willReturn(true);
        Player player = mock(Player.class);

        // when
        command.executeCommand(player, Arrays.asList(playerMail, playerMail));

        // then
        verify(validationService).validateEmail(playerMail);
        verify(commandService).send(player, MessageKey.INVALID_EMAIL);
        verifyZeroInteractions(management);
    }

    @Test
    public void shouldRejectInvalidEmailConfirmation() {
        // given
        String playerMail = "bobber@bobby.org";
        given(validationService.validateEmail(playerMail)).willReturn(true);
        given(commandService.getProperty(RegistrationSettings.REGISTRATION_TYPE)).willReturn(RegistrationArgumentType.EMAIL_WITH_CONFIRMATION);
        given(sendMailSsl.hasAllInformation()).willReturn(true);
        Player player = mock(Player.class);

        // when
        command.executeCommand(player, Arrays.asList(playerMail, "invalid"));

        // then
        verify(commandService).send(player, MessageKey.USAGE_REGISTER);
        verify(sendMailSsl).hasAllInformation();
        verifyZeroInteractions(management);
    }

    @Test
    public void shouldPerformEmailRegistration() {
        // given
        String playerMail = "asfd@lakjgre.lds";
        given(validationService.validateEmail(playerMail)).willReturn(true);
        int passLength = 7;
        given(commandService.getProperty(EmailSettings.RECOVERY_PASSWORD_LENGTH)).willReturn(passLength);

        given(commandService.getProperty(RegistrationSettings.REGISTRATION_TYPE)).willReturn(RegistrationArgumentType.EMAIL_WITH_CONFIRMATION);
        given(sendMailSsl.hasAllInformation()).willReturn(true);
        Player player = mock(Player.class);

        // when
        command.executeCommand(player, Arrays.asList(playerMail, playerMail));

        // then
        verify(validationService).validateEmail(playerMail);
        verify(sendMailSsl).hasAllInformation();
        verify(management).performRegister(eq(player), argThat(stringWithLength(passLength)), eq(playerMail), eq(true));
    }

    @Test
    public void shouldRejectInvalidPasswordConfirmation() {
        // given
        given(commandService.getProperty(RegistrationSettings.REGISTRATION_TYPE)).willReturn(RegistrationArgumentType.PASSWORD_WITH_CONFIRMATION);
        Player player = mock(Player.class);

        // when
        command.executeCommand(player, Arrays.asList("myPass", "mypass"));

        // then
        verify(commandService).send(player, MessageKey.PASSWORD_MATCH_ERROR);
        verifyZeroInteractions(management, sendMailSsl);
    }

    @Test
    public void shouldPerformPasswordValidation() {
        // given
        Player player = mock(Player.class);

        // when
        command.executeCommand(player, Collections.singletonList("myPass"));

        // then
        verify(management).performRegister(player, "myPass", "", true);
    }
}
