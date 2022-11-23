package eu.siacs.p2.controller;

import eu.siacs.p2.*;
import eu.siacs.p2.persistance.TargetStore;
import eu.siacs.p2.pojo.Service;
import eu.siacs.p2.pojo.Target;
import eu.siacs.p2.xmpp.extensions.push.Notification;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import rocks.xmpp.addr.Jid;
import rocks.xmpp.core.XmppException;
import rocks.xmpp.core.stanza.IQHandler;
import rocks.xmpp.core.stanza.model.IQ;
import rocks.xmpp.core.stanza.model.errors.Condition;
import rocks.xmpp.extensions.commands.model.Command;
import rocks.xmpp.extensions.data.model.DataForm;
import rocks.xmpp.extensions.pubsub.model.Item;
import rocks.xmpp.extensions.pubsub.model.PubSub;
import rocks.xmpp.extensions.vcard.temp.VCardManager;
import rocks.xmpp.extensions.vcard.temp.model.VCard;
import rocks.xmpp.util.concurrent.AsyncResult;

public class PushController {

    private static final String COMMAND_NODE_REGISTER_PREFIX = "register-push-";
    private static final String COMMAND_NODE_UNREGISTER_PREFIX = "unregister-push-";

    public static VCardManager vCardManager;

    public static IQHandler commandHandler =
            (iq -> {
                final Command command = iq.getExtension(Command.class);
                if (command != null && command.getAction() == Command.Action.EXECUTE) {
                    final String node = command.getNode();
                    if (node != null && node.startsWith(COMMAND_NODE_REGISTER_PREFIX)) {
                        return register(iq, command);
                    } else if (node != null && node.startsWith(COMMAND_NODE_UNREGISTER_PREFIX)) {
                        return unregister(iq, command);
                    }
                }
                return iq.createError(Condition.BAD_REQUEST);
            });
    public static IQHandler pubsubHandler =
            (iq -> {
                final PubSub pubSub = iq.getExtension(PubSub.class);
                if (pubSub != null && iq.getType() == IQ.Type.SET) {
                    final PubSub.Publish publish = pubSub.getPublish();
                    final String node = publish != null ? publish.getNode() : null;
                    final Jid jid = iq.getFrom();
                    final DataForm publishOptions = pubSub.getPublishOptions();
                    final String secret =
                            publishOptions != null ? publishOptions.findValue("secret") : null;
                    final DataForm pushSummary = findPushSummary(publish);

                    final boolean hasLastMessageBody =
                            pushSummary != null
                                    && !isNullOrEmpty(pushSummary.findValue("last-message-body"));

                    if (node != null && secret != null && jid.isBareJid()) {
                        final Jid domain = Jid.ofDomain(jid.getDomain());
                        final Target target = TargetStore.getInstance().find(domain, node);
                        if (target != null) {
                            if (secret.equals(target.getSecret())) {
                                final PushService pushService;
                                try {
                                    pushService =
                                            PushServiceManager.getPushServiceInstance(
                                                    target.getService());
                                } catch (final IllegalStateException e) {
                                    e.printStackTrace();
                                    return iq.createError(Condition.INTERNAL_SERVER_ERROR);
                                }
                                try {
                                    VCard vCard = new VCard();
                                    vCard.setFormattedName("");
                                    vCard.setUrl(new URL("https://demo.realhrsoft.com.np/media/cache/d4/f5/d4f502d370ffc969713bdb48c8f51d2b.png"));
                                    if (pushSummary != null) {
                                        Jid sender = pushSummary.findValueAsJid("last-message-sender");
                                        try {
                                            vCard = vCardManager.getVCard(sender).getResult();
                                        } catch (XmppException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                    if (pushService.push(target, pushSummary, vCard)) {
                                        return iq.createResult();
                                    } else {
                                        return iq.createError(Condition.RECIPIENT_UNAVAILABLE);
                                    }
                                } catch (TargetDeviceNotFoundException e) {
                                    return iq.createError(Condition.ITEM_NOT_FOUND);
                                } catch (MalformedURLException e) {
                                    return iq.createError(Condition.UNDEFINED_CONDITION);
                                }

                            } else {
                                return iq.createError(Condition.FORBIDDEN);
                            }
                        } else {
                            return iq.createError(Condition.ITEM_NOT_FOUND);
                        }
                    } else {
                        return iq.createError(Condition.FORBIDDEN);
                    }
                }
                return iq.createError(Condition.BAD_REQUEST);
            });

    private static DataForm findPushSummary(final PubSub.Publish publish) {
        final Item item = publish == null ? null : publish.getItem();
        final Object payload = item == null ? null : item.getPayload();
        if (payload instanceof Notification) {
            return ((Notification) payload).getPushSummary();
        } else {
            return null;
        }
    }

    private static IQ register(final IQ iq, final Command command) {
        final Optional<DataForm> optionalData =
                command.getPayloads().stream()
                        .filter(p -> p instanceof DataForm)
                        .map(p -> (DataForm) p)
                        .findFirst();
        final Jid from = iq.getFrom().asBareJid();
        if (optionalData.isPresent()) {
            final DataForm data = optionalData.get();
            final String deviceId = findDeviceId(data);
            final String token = data.findValue("token");
            final Jid muc = data.findValueAsJid("muc");

            if (isNullOrEmpty(token) || isNullOrEmpty(deviceId)) {
                return iq.createError(Condition.BAD_REQUEST);
            }

            if (muc != null && muc.isFullJid()) {
                return iq.createError(Condition.BAD_REQUEST);
            }

            final String device = Utils.combineAndHash(from.toEscapedString(), deviceId);
            final String channel;
            if (muc == null) {
                channel = "";
            } else {
                channel = Utils.combineAndHash(muc.toEscapedString(), deviceId);
            }

            final Service service;
            try {
                service = findService(COMMAND_NODE_REGISTER_PREFIX, command.getNode());
            } catch (IllegalArgumentException e) {
                return iq.createError(Condition.ITEM_NOT_FOUND);
            }

            Target target = TargetStore.getInstance().find(service, device, channel);

            if (target != null) {
                if (target.setToken(token)) {
                    if (!TargetStore.getInstance().update(target)) {
                        return iq.createError(Condition.INTERNAL_SERVER_ERROR);
                    }
                }
            } else {
                if (muc == null) {
                    target = Target.create(service, from, deviceId, token);
                } else {
                    target = Target.createMuc(service, from, muc, deviceId, token);
                }
                TargetStore.getInstance().create(target);
            }

            final Command result =
                    new Command(
                            command.getNode(),
                            String.valueOf(System.currentTimeMillis()),
                            Command.Status.COMPLETED,
                            null,
                            null,
                            Collections.singletonList(
                                    createRegistryResponseDataForm(
                                            target.getNode(), target.getSecret())));
            return iq.createResult(result);
        } else {
            return iq.createError(Condition.BAD_REQUEST);
        }
    }

    private static String findDeviceId(DataForm data) {
        final String deviceId = data.findValue("device-id");
        if (isNullOrEmpty(deviceId)) {
            return data.findValue("android-id");
        }
        return deviceId;
    }

    private static boolean isNullOrEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static Service findService(final String prefix, final String node) {
        if (isNullOrEmpty(node)) {
            throw new IllegalArgumentException("Command node can not be null or empty");
        }
        if (prefix.length() >= node.length()) {
            throw new IllegalArgumentException("Command node too short");
        }
        final String service = node.substring(prefix.length()).toUpperCase(Locale.US);
        try {
            return Service.valueOf(service);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(service + " is not a known push service");
        }
    }

    private static DataForm createRegistryResponseDataForm(String node, String secret) {
        final List<DataForm.Field> fields = new ArrayList<>();
        fields.add(DataForm.Field.builder().var("jid").value(P2.getConfiguration().jid()).build());
        fields.add(DataForm.Field.builder().var("node").value(node).build());
        fields.add(DataForm.Field.builder().var("secret").value(secret).build());
        return new DataForm(DataForm.Type.FORM, fields);
    }

    private static IQ unregister(final IQ iq, final Command command) {
        final Optional<DataForm> optionalData =
                command.getPayloads().stream()
                        .filter(p -> p instanceof DataForm)
                        .map(p -> (DataForm) p)
                        .findFirst();
        final Jid from = iq.getFrom().asBareJid();
        if (optionalData.isPresent()) {
            final Service service;
            try {
                service = findService(COMMAND_NODE_UNREGISTER_PREFIX, command.getNode());
            } catch (final IllegalArgumentException e) {
                return iq.createError(Condition.ITEM_NOT_FOUND);
            }

            final DataForm data = optionalData.get();
            final String deviceId = findDeviceId(data);
            final Jid muc = data.findValueAsJid("muc");
            if (isNullOrEmpty(deviceId)) {
                return iq.createError(Condition.BAD_REQUEST);
            }
            final String device = Utils.combineAndHash(from.toEscapedString(), deviceId);
            final boolean success;
            if (muc != null) {
                final String channel = Utils.combineAndHash(muc.toEscapedString(), deviceId);
                success = TargetStore.getInstance().delete(device, channel);
            } else {
                success = TargetStore.getInstance().delete(service, device);
            }
            if (success) {
                final Command result = new Command(command.getNode(), Command.Action.COMPLETE);
                return iq.createResult(result);
            } else {
                return iq.createError(Condition.ITEM_NOT_FOUND);
            }
        } else {
            return iq.createError(Condition.BAD_REQUEST);
        }
    }
}
