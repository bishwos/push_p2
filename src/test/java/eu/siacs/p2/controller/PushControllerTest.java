package eu.siacs.p2.controller;


import eu.siacs.p2.PushService;
import eu.siacs.p2.PushServiceManager;
import eu.siacs.p2.persistance.TargetStore;
import eu.siacs.p2.pojo.Service;
import eu.siacs.p2.pojo.Target;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import rocks.xmpp.addr.Jid;
import rocks.xmpp.core.stanza.model.IQ;
import rocks.xmpp.extensions.data.model.DataForm;
import rocks.xmpp.extensions.pubsub.PubSubManager;
import rocks.xmpp.extensions.pubsub.model.PubSub;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PushControllerTest {
    private final PubSub.Publish publish = mock(PubSub.Publish.class);
    private final PubSub pubSub = mock(PubSub.class);
    private final DataForm publishOptions = mock(DataForm.class);
    private final DataForm pushSummary = mock(DataForm.class);
    private final IQ iq = mock(IQ.class);
    private final Jid jid = Mockito.mock(Jid.class);
    private final PubSubManager pubSubManager = mock(PubSubManager.class);
    private final PushService pushService = mock(PushService.class);
    private final Target target = mock(Target.class);
    private final TargetStore targetStore = mock(TargetStore.class);
    private final PushServiceManager pushServiceManager = mock(PushServiceManager.class);


    @Test
    public void testPubSubHandler() throws Exception {
        when(iq.getExtension(PubSub.class)).thenReturn(pubSub);
        when(iq.getType()).thenReturn(IQ.Type.SET);
        when(pubSub.getPublish()).thenReturn(publish);
        when(publish.getNode()).thenReturn("node");
        when(iq.getFrom()).thenReturn(jid);
        when(jid.getDomain()).thenReturn("domain");
        when(jid.isBareJid()).thenReturn(true);
        when(pubSub.getPublishOptions()).thenReturn(publishOptions);
        when(publishOptions.findValue("secret")).thenReturn("secret");
        when(pushSummary.findValue("last-message-body")).thenReturn("Hello World");
        when(pushSummary.findValue("last-message-sender")).thenReturn("sender");
        when(target.getSecret()).thenReturn("secret");
        when(target.getService()).thenReturn(Service.FCM);
        when(targetStore.find(Jid.ofDomain("domain"), "node")).thenReturn(target);
        when(PushServiceManager.getPushServiceInstance(Service.FCM)).thenReturn(pushService);
        when(pushService.push(target, true)).thenReturn(true);
        PushController.pubsubHandler.handleRequest(iq);
        Mockito.verify(target, Mockito.times(1)).setSender("sender");
        Mockito.verify(target, Mockito.times(1)).setBody("Hello World");
        Mockito.verify(pushService, Mockito.times(1)).push(target, true);
    }

//    @Test
//    public void testPubSubHandlerInvalidRequest() throws Exception {
//        when(iq.getExtension(PubSub.class)).thenReturn(null);
//        when(iq.getType()).thenReturn(IQ.Type.SET);
//        Assert.assertNotNull(IQHandler.pubsubHandler.handleIQRequest(iq));
//    }
//
//    @Test
//    public void testPubSubHandler_withValidInput() throws Exception {
//        final IQ iq = new IQ(IQ.Type.SET);
//        final Jid from = Jid.ofDomain("example.com");
//        iq.setFrom(from);
//        final PubSub pubSub = new PubSub();
//        final PubSub.Publish publish = new PubSub.Publish();
//        publish.setNode("node1");
//        pubSub.setPublish(publish);
//        final DataForm publishOptions = new DataForm(DataForm.Type.SUBMIT);
//        publishOptions.setField("secret", "1234");
//        pubSub.setPublishOptions(publishOptions);
//        iq.addExtension(pubSub);
//
//        final IQ result = IQHandler.pubsubHandler.handleIQ(iq);
//
//        assertNotNull(result);
//        assertEquals(IQ.Type.RESULT, result.getType());
//    }
//
//    @Test
//    public void testPubSubHandler_withInvalidSecret() throws Exception {
//        final IQ iq = new IQ(IQ.Type.SET);
//        final Jid from = Jid.ofDomain("example.com");
//        iq.setFrom(from);
//        final PubSub pubSub = new PubSub();
//        final PubSub.Publish publish = new PubSub.Publish();
//        publish.setNode("node1");
//        pubSub.setPublish(publish);
//        final DataForm publishOptions = new DataForm(DataForm.Type.SUBMIT);
//        publishOptions.setField("secret", "12345");
//        pubSub.setPublishOptions(publishOptions);
//        iq.addExtension(pubSub);
//
//        final IQ result = IQHandler.pubsubHandler.handleIQ(iq);
//
//        assertNotNull(result);
//        assertEquals(IQ.Type.ERROR, result.getType());
//        assertEquals(Condition.FORBIDDEN, result.getError().getCondition());
//    }
//
//    @Test
//    public void testPubSubHandler_withInvalidInput() throws Exception {
//        final IQ iq = new IQ(IQ.Type.SET);
//
//        final IQ result = IQHandler.pubsubHandler.handleIQ(iq);
//
//        assertNotNull(result);
//        assertEquals(IQ.Type.ERROR, result.getType());
//        assertEquals(Condition.BAD_REQUEST, result.getError().getCondition());
//    }



}
