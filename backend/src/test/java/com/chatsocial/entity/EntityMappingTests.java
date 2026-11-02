package com.chatsocial.entity;

import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class EntityMappingTests {

    @TempDir
    Path tempDirectory;

    @Test
    void allEntitiesGeneratePostgresSchemaWithoutDatabaseAccess() throws Exception {
        Path schemaScript = tempDirectory.resolve("schema.sql");
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
                .applySetting(
                        "hibernate.connection.provider_class",
                        "org.hibernate.engine.jdbc.connections.internal.UserSuppliedConnectionProviderImpl"
                )
                .applySetting("hibernate.hbm2ddl.auto", "none")
                .applySetting("jakarta.persistence.schema-generation.database.action", "none")
                .applySetting("jakarta.persistence.schema-generation.scripts.action", "create")
                .applySetting(
                        "jakarta.persistence.schema-generation.scripts.create-target",
                        schemaScript.toString()
                )
                .build();

        try {
            var metadata = new MetadataSources(registry)
                    .addAnnotatedClass(User.class)
                    .addAnnotatedClass(UserProfile.class)
                    .addAnnotatedClass(UserSession.class)
                    .addAnnotatedClass(UserRefreshToken.class)
                    .addAnnotatedClass(EmailVerification.class)
                    .addAnnotatedClass(Friendship.class)
                    .addAnnotatedClass(FriendRequest.class)
                    .addAnnotatedClass(Post.class)
                    .addAnnotatedClass(PostMedia.class)
                    .addAnnotatedClass(PostLike.class)
                    .addAnnotatedClass(Comment.class)
                    .addAnnotatedClass(Conversation.class)
                    .addAnnotatedClass(ConversationMember.class)
                    .addAnnotatedClass(Message.class)
                    .addAnnotatedClass(MessageMedia.class)
                    .addAnnotatedClass(MessageRead.class)
                    .addAnnotatedClass(Notification.class)
                    .buildMetadata();

            try (var sessionFactory = metadata.buildSessionFactory()) {
                assertEquals(17, sessionFactory.getMetamodel().getEntities().size());
                assertEquals(1, metadata.getEntityBinding(PostLike.class.getName())
                        .getTable().getPrimaryKey().getColumnSpan());
                assertEquals(2, metadata.getEntityBinding(ConversationMember.class.getName())
                        .getTable().getPrimaryKey().getColumnSpan());
                assertEquals(2, metadata.getEntityBinding(MessageRead.class.getName())
                        .getTable().getPrimaryKey().getColumnSpan());

                var profileTable = metadata.getEntityBinding(UserProfile.class.getName()).getTable();
                boolean uniqueProfileUser = profileTable.getColumns().stream()
                        .anyMatch(column -> column.getName().equals("user_id") && column.isUnique())
                        || profileTable.getUniqueKeys().values().stream().anyMatch(key ->
                        key.getColumns().stream().map(column -> column.getName())
                                .collect(Collectors.toSet()).equals(Set.of("user_id")));
                assertTrue(uniqueProfileUser, "A user must have at most one profile");

                var messageTable = metadata.getEntityBinding(Message.class.getName()).getTable();
                assertTrue(messageTable.getForeignKeyCollection().stream().anyMatch(fk ->
                        fk.getReferencedTable().getName().equals("conversation_members")
                                && fk.getColumns().stream()
                                .map(column -> column.getName())
                                .collect(Collectors.toSet())
                                .equals(Set.of("conversation_id", "sender_id"))
                ), "A message sender must belong to the conversation");
            }

            String ddl = Files.readString(schemaScript).toLowerCase(Locale.ROOT);
            assertTrue(ddl.contains("references chatsocial.users"));
            assertTrue(ddl.contains("references chatsocial.posts"));
            assertTrue(ddl.contains("fk_messages_sender_member"));

            assertTrue(ddl.contains("uq_post_likes_post_user"));
            assertTrue(ddl.contains("uq_friendships_users"));
            assertTrue(ddl.contains("uq_friend_requests_pair"));
            assertTrue(ddl.contains("uq_post_media_order"));
            assertTrue(ddl.contains("user_id < friend_id"));
            assertFalse(ddl.contains("post_images"));
            assertFalse(ddl.contains("last_read_at"));
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    @Test
    void reversedFriendshipsProduceTheSameOrderedPair() throws Exception {
        User first = user("00000000-0000-0000-0000-000000000001");
        User second = user("00000000-0000-0000-0000-000000000002");
        Friendship forward = new Friendship(first, second);
        Friendship reverse = new Friendship(second, first);

        invokeCallback(forward, "normalizePair");
        invokeCallback(reverse, "normalizePair");

        assertEquals(forward.getUser().getId(), reverse.getUser().getId());
        assertEquals(forward.getFriend().getId(), reverse.getFriend().getId());
    }

    @Test
    void friendshipRejectsSelfAndUnsavedUsers() {
        User saved = user("00000000-0000-0000-0000-000000000001");
        assertThrows(IllegalStateException.class,
                () -> invokeCallback(new Friendship(saved, saved), "normalizePair"));
        assertThrows(IllegalStateException.class,
                () -> invokeCallback(new Friendship(saved, user(null)), "normalizePair"));
    }

    @Test
    void reversedRequestsProduceTheSamePairKeyWithoutChangingRequester() throws Exception {
        User first = user("00000000-0000-0000-0000-000000000001");
        User second = user("00000000-0000-0000-0000-000000000002");
        FriendRequest forward = new FriendRequest(null, first, second, null, null, null, null);
        FriendRequest reverse = new FriendRequest(null, second, first, null, null, null, null);

        invokeCallback(forward, "normalizePair");
        invokeCallback(reverse, "normalizePair");

        assertEquals(forward.getPairKey(), reverse.getPairKey());
        assertSame(first, forward.getRequester());
        assertSame(second, reverse.getRequester());
        assertNotNull(forward.getStatus());
    }

    private User user(String id) {
        return new User(id, "example@example.com", "password-hash", "example", null, null, null);
    }

    private void invokeCallback(Object entity, String methodName) throws Exception {
        var method = entity.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        try {
            method.invoke(entity);
        } catch (InvocationTargetException exception) {
            if (exception.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw exception;
        }
    }
}
