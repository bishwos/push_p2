package eu.siacs.p2.service;

import com.google.api.client.util.DateTime;
import rocks.xmpp.core.stanza.IQHandler;
import rocks.xmpp.extensions.muc.ChatRoom;
import rocks.xmpp.extensions.muc.ChatService;
import rocks.xmpp.extensions.muc.MultiUserChatManager;
import rocks.xmpp.extensions.search.SearchManager;
import rocks.xmpp.extensions.search.model.Search;
import rocks.xmpp.addr.Jid;
import rocks.xmpp.extensions.data.model.DataForm;
import rocks.xmpp.extensions.rsm.model.ResultSetManagement;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

public class UserDirectorySearch {

    public static SearchManager searchManager;

    public static MultiUserChatManager muc;
    public static HashMap<String, HashMap<String, Contact>> domainContacts = new HashMap<>();
    public static DateTime lastUpdated = new DateTime(System.currentTimeMillis());
    public static  long updateInterval = Duration.ofHours(2).toMillis();

    public static Future<Contact> getUser(Jid jid) {
        CompletableFuture<Contact> completable = new CompletableFuture<>();


        HashMap<String, Contact> contacts = domainContacts.computeIfAbsent(jid.getDomain(), k -> new HashMap<>());
        Contact from = contacts.get(jid.asBareJid().toString());
        if (from != null && lastUpdated.getValue() - System.currentTimeMillis() < updateInterval) {
            completable.complete(from);
            return completable;
        }

        searchManager.setEnabled(true);

        List<DataForm.Field> fieldList = new ArrayList<>();
        fieldList.add(DataForm.Field.builder().label("user").value("*").build());
        DataForm form = new DataForm(DataForm.Type.SUBMIT, fieldList);

        Search search = new Search("", "", "", "", ResultSetManagement.forFirstPage(10000), "", form);
        return searchManager.search(search, Jid.of("vjud." + jid.getDomain())).thenCompose(searchResult -> {
            updateContacts(contacts, searchResult);
            ChatService chatService = muc.createChatService(Jid.of("conference." + jid.getDomain()));
            return chatService.discoverRooms();
        }).thenApply((List<ChatRoom> chatRooms) -> {
            updateRooms(contacts, chatRooms);
            return contacts.get(jid.asBareJid().toString());
        });
    }

    private static void updateRooms(HashMap<String, Contact> contacts, List<ChatRoom> chatRooms) {
        for (ChatRoom chatRoom : chatRooms) {
            contacts.put(chatRoom.getAddress().toString(), new Contact(chatRoom.getAddress(), chatRoom.getName()));
        }
    }

    private static void updateContacts(HashMap<String, Contact> contacts, Search searchResult) {
        searchResult.getAdditionalInformation().getItems().forEach(item -> {
            Contact contact = new Contact(item.getFields());
            contacts.put(contact.getJid().toString(), contact);
        });
    }

    public static IQHandler searchHandler =
            (iq -> {
                final Search searchResult = iq.getExtension(Search.class);
                searchResult.getItems().forEach(item -> {
                    System.out.println(item.getJid());
                });
                return null;
            });

    public static class Contact {
        private String name;
        private Jid jid;
        private String profilePicture;
        private String title;
        private DateTime updated = new DateTime(System.currentTimeMillis());

        public Contact(List<DataForm.Field> fields) {
            for (DataForm.Field field : fields) {
                switch (field.getVar()) {
                    case "name":
                        this.name = field.getVar();
                        break;
                    case "jid":
                        this.jid = Jid.of(field.getVar());
                        break;
                    case "profile_picture":
                        this.profilePicture = field.getVar();
                        break;
                    case "title":
                        this.title = field.getVar();
                        break;
                }
            }
        }

        public Contact(Jid jid, String name) {
            this.jid = jid;
            this.title = "";
            this.name = name;
            this.profilePicture = "";
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Jid getJid() {
            return jid;
        }

        public void setJid(Jid jid) {
            this.jid = jid;
        }

        public String getProfilePicture() {
            return profilePicture;
        }

        public void setProfilePicture(String profilePicture) {
            this.profilePicture = profilePicture;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public DateTime getUpdated() {
            return updated;
        }

        public void setUpdated(DateTime updated) {
            this.updated = updated;
        }
    }
}
