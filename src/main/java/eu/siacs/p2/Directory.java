package eu.siacs.p2;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import rocks.xmpp.addr.Jid;
import rocks.xmpp.core.XmppException;
import rocks.xmpp.extensions.component.accept.ExternalComponent;
import rocks.xmpp.extensions.data.model.DataForm;
import rocks.xmpp.extensions.disco.ServiceDiscoveryManager;
import rocks.xmpp.extensions.disco.model.info.Identity;
import rocks.xmpp.extensions.disco.model.info.InfoNode;
import rocks.xmpp.extensions.disco.model.items.Item;
import rocks.xmpp.extensions.disco.model.items.ItemNode;
import rocks.xmpp.extensions.rsm.model.ResultSetManagement;
import rocks.xmpp.extensions.search.SearchManager;
import rocks.xmpp.extensions.search.model.Search;
import rocks.xmpp.util.concurrent.AsyncResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class Directory {
    static ServiceDiscoveryManager manager;
    static SearchManager searchManager;

    private static final HashMap<String, String> rooms = new HashMap<>();
    private static final HashMap<String, Contact> directory = new HashMap<>();
    private static final List<String> unknowns = new ArrayList<>();
    private static boolean publicRoomsUpdated = false;

    static void init(ExternalComponent externalComponent) {
        manager = externalComponent.getManager(ServiceDiscoveryManager.class);
        searchManager = externalComponent.getManager(SearchManager.class);
    }

    public static String getName(String jid) {
        Jid from = Jid.of(jid);
        String bareJid = from.asBareJid().toString();
        if (bareJid.contains("@conference")) {
            if (!rooms.containsKey(bareJid)) {
                updatePublicRooms(from.getDomain());
            }
            if (!rooms.containsKey(bareJid)) {
                getRoomInfo(from);
            }
            String name = rooms.get(bareJid);
            if (name != null) {
                return name;
            }
        }
        if (!directory.containsKey(bareJid)) {
            updateDirectory(from.getDomain());
        }
        Contact item = directory.get(bareJid);
        if (item != null) {
            return item.getName();
        }
        unknowns.add(bareJid);
        return jid;
    }

    public static String getProfilePicture(String jid) {
        Jid of = Jid.of(jid);
        String bareJid = of.asBareJid().toString();
        if (bareJid.contains("@conference")) {
            return null;
        }
        Contact contact = directory.get(bareJid);
        if (contact == null) {
            return null;
        }
        return contact.getProfile();
    }


    private static void updateDirectory(String domain) {
        System.out.println("Updating directory for " + domain);
        DataForm.Field hidden = DataForm
                .Field
                .builder()
                .type(DataForm.Field.Type.HIDDEN)
                .var("FORM_TYPE")
                .value("jabber:iq:search").build();

        List<DataForm.Field> fields = List.of(hidden);
        DataForm dataForm = new DataForm(DataForm.Type.SUBMIT, fields);

        Search searchRequest = new Search("", "", "", "", ResultSetManagement.forItemCount(), "", dataForm);
        AsyncResult<Search> search = searchManager.search(searchRequest, Jid.of("vjud." + domain));
        try {
            Search result = search.getResult(60, TimeUnit.SECONDS);
            JsonParser jsonParser = new JsonParser();
            for (DataForm.Item item : result.getAdditionalInformation().getItems()) {
                Contact contact = new Contact();
                for (DataForm.Field field : item.getFields()) {
                    switch (field.getVar()) {
                        case "jid":
                            contact.setJid(field.getValue());
                            break;
                        case "fn":
                            contact.setName(field.getValue());
                            break;
                        case "middle":
                            String value = field.getValue();
                            JsonObject asJsonObject = jsonParser.parse(value).getAsJsonObject();
                            if (asJsonObject.keySet().contains("profile_picture")) {
                                String profilePicture = asJsonObject.get("profile_picture").getAsString();
                                contact.setProfile(profilePicture);
                            }
                            break;
//                        case "orgunit":
//                            contact.setTitle(field.getValue());
//                            break;
                    }
                }
                directory.put(contact.getJid(), contact);
            }
        } catch (XmppException | TimeoutException e) {
            e.printStackTrace();
        }
    }

    private static void updatePublicRooms(String domain) {
        if (publicRoomsUpdated) {
            return;
        }
        System.out.println("Updating public rooms");
        try {
            ItemNode result = manager.discoverItems(Jid.of(domain)).getResult();
            List<Item> items = result.getItems();

            for (Item item : items) {
                System.out.println(item.getName());
                rooms.put(item.getJid().toString(), item.getName());
            }
            publicRoomsUpdated = true;
        } catch (XmppException e) {
            e.printStackTrace();
        }
    }

    private static void getRoomInfo(Jid room) {
        System.out.println("Getting room info");
        try {
            InfoNode result = manager.discoverInformation(room.asBareJid()).getResult();
            for (Identity identity : result.getIdentities()) {
                rooms.put(room.asBareJid().toString(), identity.getName());
            }
        } catch (XmppException e) {
            e.printStackTrace();
        }
    }

    static class Contact {
        private String jid;
        private String name;
        private String profile;
//        private String title;

        public String getJid() {
            return jid;
        }

        public void setJid(String jid) {
            this.jid = jid;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getProfile() {
            return profile;
        }

        public void setProfile(String profile) {
            this.profile = profile;
        }

//        public String getTitle() {
//            return title;
//        }

//        public void setTitle(String title) {
//            this.title = title;
//        }
    }
}
