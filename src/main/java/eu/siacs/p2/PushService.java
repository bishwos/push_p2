package eu.siacs.p2;

import eu.siacs.p2.pojo.Target;
import rocks.xmpp.extensions.data.model.DataForm;

public interface PushService {

    boolean push(Target target, DataForm pushSummary) throws TargetDeviceNotFoundException;
}
