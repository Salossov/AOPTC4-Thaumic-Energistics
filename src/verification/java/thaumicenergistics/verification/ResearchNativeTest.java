package thaumicenergistics.verification;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.api.research.*;
import thaumicenergistics.ThaumicEnergistics;

@GameTestHolder(ThaumicEnergistics.ID)
@PrefixGameTestTemplate(false)
public final class ResearchNativeTest {
    public static final String ALIAS="thaumicenergistics:qa_linked_research";
    public static final String TARGET="BOTTLETAINT";
    @GameTest(template="empty",timeoutTicks=120)
    public static void nativeLinkedResearchCompletion(GameTestHelper h) {
        VerificationReports.install();
        var cookie=net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"THEResearchTest"),false);
        var player=new net.minecraft.server.level.ServerPlayer(h.getLevel().getServer(),h.getLevel(),cookie.gameProfile(),cookie.clientInformation());
        // GameTest's mock login has no negotiated custom-payload channels. This fixture
        // tests real server transactions; the separate real client checks wire delivery.
        player.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(player.server,
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),player,cookie) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}
        };
        try {
            var definitions=ResearchApi.definitions(player);var alias=definitions.get(ALIAS);var target=definitions.get(TARGET);
            h.assertTrue(alias!=null&&alias.linkedResearch().equals(TARGET)&&target!=null,"verification JSON loaded optional linked_research");
            h.assertTrue(alias.category().equals("thaumicenergistics:qa_links")&&target.category().equals("ALCHEMY"),"alias and native node remain in separate categories");
            h.assertTrue(alias.icon().equals(target.icon())&&alias.pageOrder().equals(target.pageOrder())&&alias.recipeGroups().equals(target.recipeGroups()),"native active catalog resolves icon and all pages");
            var before=ResearchApi.knowledge(player);
            h.assertTrue(!before.completedResearch().contains(ALIAS)&&!alias.available(before),"alias cannot be independently bought before target");
            h.assertTrue(ResearchApi.grant(player,TARGET),"native ordinary completion accepted");
            var after=ResearchApi.knowledge(player);
            h.assertTrue(after.completedResearch().contains(ALIAS)&&after.completedResearch().contains(TARGET),"native knowledge transaction mirrors alias");
            h.assertTrue(after.permanentWarp()-before.permanentWarp()==1&&after.stickyWarp()-before.stickyWarp()==1,"only original Bottle of Taint warp2 is awarded; alias warp127 is ignored");
            h.assertTrue(after.aspectPools().equals(before.aspectPools()),"alias consumes no extra aspect points");
            h.assertTrue(!ResearchApi.grant(player,TARGET)&&ResearchApi.knowledge(player).revision()==after.revision(),"repeated completion adds no warp or knowledge transaction");
            System.out.println("THE_NATIVE_LINKED_RESEARCH_PASS");h.succeed();
        } finally {player.connection=null;}
    }
    @GameTest(template="empty",timeoutTicks=120)
    @SuppressWarnings("unchecked")
    public static void nativeFilteredLinkCatalogRollback(GameTestHelper h)throws Exception {
        VerificationReports.install();
        var cookie=net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"THECatalogTest"),false);
        var player=new net.minecraft.server.level.ServerPlayer(h.getLevel().getServer(),h.getLevel(),cookie.gameProfile(),cookie.clientInformation());
        var previous=ResearchApi.definitions(player);
        // Exercise the real post-tag/config resolution boundary. Raw malformed packs are
        // already rejected by the loader tests. Reflection is confined to this fixture;
        // gameplay code only uses the public API and never replaces the core catalog.
        var dataClass=Class.forName("thaumcraft.data.ThaumcraftData");
        var dataField=dataClass.getDeclaredField("SERVERS");dataField.setAccessible(true);
        var bookField=dataClass.getDeclaredField("BOOKS");bookField.setAccessible(true);
        var snapshots=(java.util.Map<Object,Object>)dataField.get(null);var books=(java.util.Map<Object,Object>)bookField.get(null);
        var manager=h.getLevel().getRecipeManager();var original=snapshots.get(manager);var originalBooks=books.get(manager);
        var filtered=new java.util.LinkedHashMap<>(previous);filtered.remove(TARGET);
        var components=original.getClass().getRecordComponents();var values=new Object[components.length];var types=new Class<?>[components.length];
        for(int i=0;i<components.length;i++) {
            types[i]=components[i].getType();values[i]=components[i].getAccessor().invoke(original);
            if(components[i].getName().equals("research"))values[i]=filtered;
            if(components[i].getName().equals("resolved"))values[i]=false;
        }
        try {
            snapshots.put(manager,original.getClass().getConstructor(types).newInstance(values));
            var book=(thaumcraft.api.book.BookCatalog)originalBooks;
            books.put(manager,new thaumcraft.api.book.BookCatalog(book.categories(),book.pages()));
            h.assertTrue(dataClass.getMethod("books",net.minecraft.server.MinecraftServer.class).invoke(null,player.server)==originalBooks,
                    "book-first lookup resolves or rolls back the entire pending catalog before returning categories");
            h.assertTrue(ResearchApi.definitions(player).equals(previous),"missing post-filter target retains last valid catalog");
            h.assertTrue(snapshots.get(manager)==original&&books.get(manager)==originalBooks,"rollback retains matching research and book snapshots atomically");
            System.out.println("THE_NATIVE_LINKED_CATALOG_ROLLBACK_PASS");h.succeed();
        } finally {snapshots.put(manager,original);books.put(manager,originalBooks);}
    }
}
