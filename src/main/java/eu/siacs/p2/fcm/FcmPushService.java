package eu.siacs.p2.fcm;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.*;
import eu.siacs.p2.Directory;
import eu.siacs.p2.PushService;
import eu.siacs.p2.TargetDeviceNotFoundException;
import eu.siacs.p2.pojo.Target;

import java.io.FileInputStream;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rocks.xmpp.addr.Jid;

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

    @Override
    public boolean push(Target target, boolean highPriority) throws TargetDeviceNotFoundException {
        final String account = target.getDevice();

        final String channel =
                target.getChannel() == null || target.getChannel().isEmpty()
                        ? null
                        : target.getChannel();


        String body = target.getBody();
        String sender = target.getSender();
        String senderName = Directory.getName(sender);
        String profilePicture = Directory.getProfilePicture(sender);

        //TODO: set channel using target.getChannel()
        AndroidNotification.Builder androidNotification = AndroidNotification.builder()
                .setBody(target.getBody())
                .setTitle(senderName)
                .setDefaultSound(true)
                .setSound("default")
                .setClickAction("FLUTTER_NOTIFICATION_CLICK")
                .setIcon("launch_background")
                .setDefaultVibrateTimings(true);

        Notification.Builder notification = Notification.builder();
        notification.setTitle(senderName);
        notification.setBody(body);

        if (profilePicture != null) {
            androidNotification.setImage(profilePicture);
            notification.setImage(profilePicture);
        }

        if (isLink(body) && isImage(body)) {
            notification.setImage(body).setBody("sent an image");
            androidNotification.setImage(body).setBody("sent an image");
        }

        if (isLink(body) && !isImage(body)) {
            notification.setBody("sent a attachment");
            androidNotification.setBody("sent an attachment");
        }

        if (body.contains("invites you to the room")) {
            String inviter = body.split(" ")[0];
            String inviterName = Directory.getName(inviter);
            notification.setBody(inviterName + " added you to this group");
            androidNotification.setBody(inviterName + " added you to this group");
        }


        final Message.Builder message =
                Message.builder()
                        .setNotification(notification.build())
                        .setToken(target.getToken())
                        .putData("from_jid", target.getSender())
                        .putData("click_action", "FLUTTER_NOTIFICATION_CLICK")
                        .setAndroidConfig(
                                AndroidConfig.builder().setNotification(androidNotification.build()).build())
                        .putData("account", account);
        if (channel != null) {
            message.putData("channel", channel);
        }
        return push(message.build());
    }

    boolean isLink(String body) {
        //TODO: check if body has domain
        return body.startsWith("http://") || body.startsWith("https://") && !body.contains(" ") && body.contains("/upload");
    }

    boolean isImage(String body) {
        return body.endsWith(".jpg") || body.endsWith(".jpeg") || body.endsWith(".png") || body.endsWith(".gif");
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
