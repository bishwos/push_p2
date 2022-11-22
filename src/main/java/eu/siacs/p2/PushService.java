package eu.siacs.p2;

import eu.siacs.p2.pojo.Target;
import rocks.xmpp.extensions.data.model.DataForm;
import rocks.xmpp.extensions.vcard.temp.model.VCard;

public interface PushService {

    boolean push(Target target, DataForm pushSummary, VCard vCard) throws TargetDeviceNotFoundException;
}
