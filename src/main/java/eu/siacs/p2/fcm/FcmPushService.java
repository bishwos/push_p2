package eu.siacs.p2.fcm;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.*;
import eu.siacs.p2.PushService;
import eu.siacs.p2.TargetDeviceNotFoundException;
import eu.siacs.p2.pojo.Target;
import java.io.FileInputStream;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rocks.xmpp.extensions.data.model.DataForm;

public class FcmPushService implements PushService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FcmPushService.class);

    private final FcmConfiguration configuration;

    public FcmPushService(final FcmConfiguration config) {
        try {
            FirebaseOptions options =
                    FirebaseOptions.builder()
                            .setCredentials(
                                    GoogleCredentials.fromStream(
                                            new FileInputStream(config.serviceAccountFile())))
                            .build();
            FirebaseApp.initializeApp(options);
        } catch (final IOException e) {
            LOGGER.error("Unable to initialize firebase app", e);
        }
        this.configuration = config;
    }

    private static boolean isNullOrEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    @Override
    public boolean push(Target target, DataForm pushSummary) throws TargetDeviceNotFoundException {
        final String account = target.getDevice();

        final String channel =
                target.getChannel() == null || target.getChannel().isEmpty()
                        ? null
                        : target.getChannel();
        final String collapseKey;
        if (configuration.collapse()) {
            if (channel == null) {
                collapseKey = account.substring(0, 6);
            } else {
                collapseKey = channel.substring(0, 6);
            }
        } else {
            collapseKey = null;
        }
        String body = pushSummary.findValue("last-message-body");
        String title = pushSummary.getTitle();
        if (isNullOrEmpty(body)) {
            body = "New Message is here";
        }
        if (isNullOrEmpty(title)) {
            title = pushSummary.findValue("last-message-sender");
        }
        final Message.Builder message =
                Message.builder()
                        .setNotification(Notification.builder().setBody(body).setTitle(title).build())
                        .putData("title", title)
                        .putData("body", body)
                        .setToken(target.getToken())
                        .setAndroidConfig(
                                AndroidConfig.builder().setCollapseKey(collapseKey).build())
                        .putData("account", account);
        if (channel != null) {
            message.putData("channel", channel);
        }
        return push(message.build());
    }

    private boolean push(Message message) throws TargetDeviceNotFoundException {
        try {
            FirebaseMessaging.getInstance().send(message);
            return true;
        } catch (final FirebaseMessagingException e) {
            if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
                throw new TargetDeviceNotFoundException(e);
            } else {
                LOGGER.warn("push to FCM failed with unknown messaging error code", e);
                return false;
            }
        }
    }
}
