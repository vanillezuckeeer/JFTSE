package com.ft.emulator.server.game.core.game.handler;

import com.ft.emulator.common.utilities.BitKit;
import com.ft.emulator.common.utilities.StreamUtils;
import com.ft.emulator.common.utilities.StringUtils;
import com.ft.emulator.server.database.model.account.Account;
import com.ft.emulator.server.database.model.challenge.Challenge;
import com.ft.emulator.server.database.model.challenge.ChallengeProgress;
import com.ft.emulator.server.database.model.home.AccountHome;
import com.ft.emulator.server.database.model.home.HomeInventory;
import com.ft.emulator.server.database.model.item.ItemHouse;
import com.ft.emulator.server.database.model.item.ItemHouseDeco;
import com.ft.emulator.server.database.model.item.Product;
import com.ft.emulator.server.database.model.player.ClothEquipment;
import com.ft.emulator.server.database.model.player.Player;
import com.ft.emulator.server.database.model.player.QuickSlotEquipment;
import com.ft.emulator.server.database.model.player.StatusPointsAddedDto;
import com.ft.emulator.server.database.model.pocket.PlayerPocket;
import com.ft.emulator.server.database.model.pocket.Pocket;
import com.ft.emulator.server.database.model.tutorial.TutorialProgress;
import com.ft.emulator.server.game.core.constants.RoomPositionState;
import com.ft.emulator.server.game.core.constants.RoomStatus;
import com.ft.emulator.server.game.core.item.EItemCategory;
import com.ft.emulator.server.game.core.item.EItemHouseDeco;
import com.ft.emulator.server.game.core.item.EItemUseType;
import com.ft.emulator.server.game.core.matchplay.room.Room;
import com.ft.emulator.server.game.core.matchplay.room.RoomPlayer;
import com.ft.emulator.server.game.core.packet.PacketID;
import com.ft.emulator.server.game.core.packet.packets.S2CDisconnectAnswerPacket;
import com.ft.emulator.server.game.core.packet.packets.S2CWelcomePacket;
import com.ft.emulator.server.game.core.packet.packets.authserver.S2CLoginAnswerPacket;
import com.ft.emulator.server.game.core.packet.packets.authserver.gameserver.C2SGameServerLoginPacket;
import com.ft.emulator.server.game.core.packet.packets.authserver.gameserver.C2SGameServerRequestPacket;
import com.ft.emulator.server.game.core.packet.packets.authserver.gameserver.S2CGameServerAnswerPacket;
import com.ft.emulator.server.game.core.packet.packets.authserver.gameserver.S2CGameServerLoginPacket;
import com.ft.emulator.server.game.core.packet.packets.battle.C2SQuickSlotUseRequestPacket;
import com.ft.emulator.server.game.core.packet.packets.challenge.*;
import com.ft.emulator.server.game.core.packet.packets.chat.*;
import com.ft.emulator.server.game.core.packet.packets.home.C2SHomeItemsPlaceReqPacket;
import com.ft.emulator.server.game.core.packet.packets.home.S2CHomeDataPacket;
import com.ft.emulator.server.game.core.packet.packets.home.S2CHomeItemsLoadAnswerPacket;
import com.ft.emulator.server.game.core.packet.packets.inventory.*;
import com.ft.emulator.server.game.core.packet.packets.lobby.C2SLobbyUserListRequestPacket;
import com.ft.emulator.server.game.core.packet.packets.lobby.S2CLobbyUserListAnswerPacket;
import com.ft.emulator.server.game.core.packet.packets.lobby.room.*;
import com.ft.emulator.server.game.core.packet.packets.lottery.C2SOpenGachaReqPacket;
import com.ft.emulator.server.game.core.packet.packets.lottery.S2COpenGachaAnswerPacket;
import com.ft.emulator.server.game.core.packet.packets.player.C2SPlayerStatusPointChangePacket;
import com.ft.emulator.server.game.core.packet.packets.player.S2CPlayerLevelExpPacket;
import com.ft.emulator.server.game.core.packet.packets.player.S2CPlayerStatusPointChangePacket;
import com.ft.emulator.server.game.core.packet.packets.player.S2CShopMoneyAnswerPacket;
import com.ft.emulator.server.game.core.packet.packets.shop.*;
import com.ft.emulator.server.game.core.packet.packets.tutorial.C2STutorialBeginRequestPacket;
import com.ft.emulator.server.game.core.packet.packets.tutorial.C2STutorialEndPacket;
import com.ft.emulator.server.game.core.packet.packets.tutorial.S2CTutorialProgressAnswerPacket;
import com.ft.emulator.server.game.core.service.*;
import com.ft.emulator.server.game.core.singleplay.challenge.ChallengeBasicGame;
import com.ft.emulator.server.game.core.singleplay.challenge.ChallengeBattleGame;
import com.ft.emulator.server.game.core.singleplay.challenge.GameMode;
import com.ft.emulator.server.game.core.singleplay.tutorial.TutorialGame;
import com.ft.emulator.server.networking.Connection;
import com.ft.emulator.server.networking.packet.Packet;
import com.ft.emulator.server.shared.module.Client;
import com.ft.emulator.server.shared.module.GameHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.ReflectionUtils;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GamePacketHandler {
    private final GameHandler gameHandler;

    private final AuthenticationService authenticationService;
    private final PlayerService playerService;
    private final ClothEquipmentService clothEquipmentService;
    private final QuickSlotEquipmentService quickSlotEquipmentService;
    private final PocketService pocketService;
    private final HomeService homeService;
    private final PlayerPocketService playerPocketService;
    private final ChallengeService challengeService;
    private final TutorialService tutorialService;
    private final ProductService productService;
    private final LotteryService lotteryService;

    public GameHandler getGameHandler() {
        return gameHandler;
    }

    public void sendWelcomePacket(Connection connection) {
        S2CWelcomePacket welcomePacket = new S2CWelcomePacket(0, 0, 0, 0);
        connection.sendTCP(welcomePacket);
    }

    public void handleGameServerLoginPacket(Connection connection, Packet packet) {
        C2SGameServerLoginPacket gameServerLoginPacket = new C2SGameServerLoginPacket(packet);

        Player player = playerService.findByIdFetched((long) gameServerLoginPacket.getPlayerId());
        if (player != null && player.getAccount() != null) {
            Client client = connection.getClient();
            Account account = player.getAccount();

            // set last login date
            account.setLastLogin(new Date());
            // mark as logged in
            account.setStatus((int) S2CLoginAnswerPacket.ACCOUNT_ALREADY_LOGGED_IN);
            account = authenticationService.updateAccount(account);

            client.setAccount(account);
            client.setActivePlayer(player);
            connection.setClient(client);

            S2CGameServerLoginPacket gameServerLoginAnswerPacket = new S2CGameServerLoginPacket((char) 0, (byte) 1);
            connection.sendTCP(gameServerLoginAnswerPacket);
        }
        else {
            S2CGameServerLoginPacket gameServerLoginAnswerPacket = new S2CGameServerLoginPacket((char) -1, (byte) 0);
            connection.sendTCP(gameServerLoginAnswerPacket);
        }
    }

    public void handleGameServerDataRequestPacket(Connection connection, Packet packet) {
        Client client = connection.getClient();
        Player player = client.getActivePlayer();
        Account account = client.getAccount();

        C2SGameServerRequestPacket gameServerRequestPacket = new C2SGameServerRequestPacket(packet);
        byte requestType = gameServerRequestPacket.getRequestType();

        // init data request packets and pass level & exp and home/house data
        if (requestType == 0) {
            S2CGameServerAnswerPacket gameServerAnswerPacket = new S2CGameServerAnswerPacket(requestType, (byte) 0);
            connection.sendTCP(gameServerAnswerPacket);

            S2CPlayerLevelExpPacket playerLevelExpPacket = new S2CPlayerLevelExpPacket(player.getLevel(), player.getExpPoints());
            connection.sendTCP(playerLevelExpPacket);

            AccountHome accountHome = homeService.findAccountHomeByAccountId(account.getId());

            S2CHomeDataPacket homeDataPacket = new S2CHomeDataPacket(accountHome);
            connection.sendTCP(homeDataPacket);
        }
        else if (requestType == 1) {
            S2CGameServerAnswerPacket gameServerAnswerPacket = new S2CGameServerAnswerPacket(requestType, (byte) 0);
            connection.sendTCP(gameServerAnswerPacket);
        }
        // pass inventory & equipped items
        else if (requestType == 2) {
            S2CGameServerAnswerPacket gameServerAnswerPacket = new S2CGameServerAnswerPacket(requestType, (byte) 0);
            connection.sendTCP(gameServerAnswerPacket);

            List<PlayerPocket> playerPocketList = playerPocketService.getPlayerPocketItems(player.getPocket());
            StreamUtils.batches(playerPocketList, 10).forEach(pocketList -> {
                    S2CInventoryDataPacket inventoryDataPacket = new S2CInventoryDataPacket(pocketList);
                    connection.sendTCP(inventoryDataPacket);
                });

            StatusPointsAddedDto statusPointsAddedDto = clothEquipmentService.getStatusPointsFromCloths(player);
            Map<String, Integer> equippedCloths = clothEquipmentService.getEquippedCloths(player);
            List<Integer> equippedQuickSlots = quickSlotEquipmentService.getEquippedQuickSlots(player);

            S2CPlayerStatusPointChangePacket playerStatusPointChangePacket = new S2CPlayerStatusPointChangePacket(player, statusPointsAddedDto);
            connection.sendTCP(playerStatusPointChangePacket);

            S2CInventoryWearClothAnswerPacket inventoryWearClothAnswerPacket = new S2CInventoryWearClothAnswerPacket((char) 0, equippedCloths, player, statusPointsAddedDto);
            connection.sendTCP(inventoryWearClothAnswerPacket);

            S2CInventoryWearQuickAnswerPacket inventoryWearQuickAnswerPacket = new S2CInventoryWearQuickAnswerPacket(equippedQuickSlots);
            connection.sendTCP(inventoryWearQuickAnswerPacket);
        }
        else {
            S2CGameServerAnswerPacket gameServerAnswerPacket = new S2CGameServerAnswerPacket(requestType, (byte) 0);
            connection.sendTCP(gameServerAnswerPacket);
        }
    }

    public void handleHomeItemsLoadRequestPacket(Connection connection, Packet packet) {
        AccountHome accountHome = homeService.findAccountHomeByAccountId(connection.getClient().getAccount().getId());
        List<HomeInventory> homeInventoryList = homeService.findAllByAccountHome(accountHome);

        S2CHomeItemsLoadAnswerPacket homeItemsLoadAnswerPacket = new S2CHomeItemsLoadAnswerPacket(homeInventoryList);
        connection.sendTCP(homeItemsLoadAnswerPacket);
    }

    public void handleHomeItemsPlaceRequestPacket(Connection connection, Packet packet) {
        C2SHomeItemsPlaceReqPacket homeItemsPlaceReqPacket = new C2SHomeItemsPlaceReqPacket(packet);
        List<Map<String, Object>> homeItemDataList = homeItemsPlaceReqPacket.getHomeItemDataList();

        AccountHome accountHome = homeService.findAccountHomeByAccountId(connection.getClient().getAccount().getId());

        homeItemDataList.forEach(hidl -> {
                int inventoryItemId = (int)hidl.get("inventoryItemId");

                PlayerPocket playerPocket = playerPocketService.getItemAsPocket((long) inventoryItemId, connection.getClient().getActivePlayer().getPocket());
                if (playerPocket != null) {
                    int itemCount = playerPocket.getItemCount();

                    // those items are deco items -> its placed on the wall
                    if (itemCount % 3 != 0)
                        --itemCount;
                    else
                        itemCount = 0;

                    if (itemCount == 0) {
                        playerPocketService.remove((long) inventoryItemId);
                        pocketService.decrementPocketBelongings(connection.getClient().getActivePlayer().getPocket());

                        S2CInventoryItemRemoveAnswerPacket inventoryItemRemoveAnswerPacket = new S2CInventoryItemRemoveAnswerPacket(inventoryItemId);
                        connection.sendTCP(inventoryItemRemoveAnswerPacket);
                    }
                    else {
                        playerPocket.setItemCount(itemCount);
                        playerPocketService.save(playerPocket);
                    }

                    int itemIndex = (int) hidl.get("itemIndex");
                    byte unk0 = (byte) hidl.get("unk4");
                    byte unk1 = (byte) hidl.get("unk5");
                    byte xPos = (byte) hidl.get("xPos");
                    byte yPos = (byte) hidl.get("yPos");

                    HomeInventory homeInventory = new HomeInventory();
                    homeInventory.setId((long) inventoryItemId);
                    homeInventory.setAccountHome(accountHome);
                    homeInventory.setItemIndex(itemIndex);
                    homeInventory.setUnk0(unk0);
                    homeInventory.setUnk1(unk1);
                    homeInventory.setXPos(xPos);
                    homeInventory.setYPos(yPos);

                    homeInventory = homeService.save(homeInventory);

                    homeService.updateAccountHomeStatsByHomeInventory(accountHome, homeInventory, true);
                }
            });

        S2CHomeDataPacket homeDataPacket = new S2CHomeDataPacket(accountHome);
        connection.sendTCP(homeDataPacket);
    }

    public void handleHomeItemClearRequestPacket(Connection connection, Packet packet) {

        AccountHome accountHome = homeService.findAccountHomeByAccountId(connection.getClient().getAccount().getId());
        List<HomeInventory> homeInventoryList = homeService.findAllByAccountHome(accountHome);

        homeInventoryList.forEach(hil -> {
                PlayerPocket playerPocket = playerPocketService.getItemAsPocketByItemIndexAndPocket(hil.getItemIndex(), connection.getClient().getActivePlayer().getPocket());
                ItemHouseDeco itemHouseDeco = homeService.findItemHouseDecoByItemIndex(hil.getItemIndex());

                // create a new one if null, null indicates that all items are placed
                if (playerPocket == null) {
                    playerPocket = new PlayerPocket();
                    playerPocket.setItemIndex(hil.getItemIndex());
                    playerPocket.setPocket(connection.getClient().getActivePlayer().getPocket());
                    playerPocket.setItemCount(itemHouseDeco.getKind().equals(EItemHouseDeco.DECO.getName()) ? 3 : 1);
                    playerPocket.setCategory(EItemCategory.HOUSE_DECO.getName());
                    playerPocket.setUseType(StringUtils.firstCharToUpperCase(EItemUseType.COUNT.getName().toLowerCase()));

                    pocketService.incrementPocketBelongings(connection.getClient().getActivePlayer().getPocket());
                }
                else {
                    playerPocket.setItemCount(playerPocket.getItemCount() + (itemHouseDeco.getKind().equals(EItemHouseDeco.DECO.getName()) ? 3 : 1));
                }

                playerPocketService.save(playerPocket);

                homeService.updateAccountHomeStatsByHomeInventory(accountHome, hil, false);
                homeService.removeItemFromHomeInventory(hil.getId());
            });

        S2CHomeItemsLoadAnswerPacket homeItemsLoadAnswerPacket = new S2CHomeItemsLoadAnswerPacket(new ArrayList<>());
        connection.sendTCP(homeItemsLoadAnswerPacket);

        S2CHomeDataPacket homeDataPacket = new S2CHomeDataPacket(accountHome);
        connection.sendTCP(homeDataPacket);

        List<PlayerPocket> playerPocketList = playerPocketService.getPlayerPocketItems(connection.getClient().getActivePlayer().getPocket());
        StreamUtils.batches(playerPocketList, 10)
            .forEach(pocketList -> {
                    S2CInventoryDataPacket inventoryDataPacket = new S2CInventoryDataPacket(pocketList);
                    connection.sendTCP(inventoryDataPacket);
            });
    }

    public void handleInventoryItemSellPackets(Connection connection, Packet packet) {
        switch (packet.getPacketId()) {
        case PacketID.C2SInventorySellReq: {
            byte status = S2CInventorySellAnswerPacket.SUCCESS;

            C2SInventorySellReqPacket inventorySellReqPacket = new C2SInventorySellReqPacket(packet);
            int itemPocketId = inventorySellReqPacket.getItemPocketId();

            PlayerPocket playerPocket = playerPocketService.getItemAsPocket((long) itemPocketId, connection.getClient().getActivePlayer().getPocket());

            if(playerPocket == null) {
                status = S2CInventorySellAnswerPacket.NO_ITEM;

                S2CInventorySellAnswerPacket inventorySellAnswerPacket = new S2CInventorySellAnswerPacket(status, 0, 0);
                connection.sendTCP(inventorySellAnswerPacket);
                break;
            }

            int sellPrice = playerPocketService.getSellPrice(playerPocket);

            S2CInventorySellAnswerPacket inventorySellAnswerPacket = new S2CInventorySellAnswerPacket(status, itemPocketId, sellPrice);
            connection.sendTCP(inventorySellAnswerPacket);
        } break;
        case PacketID.C2SInventorySellItemCheckReq: {
            byte status = S2CInventorySellItemCheckAnswerPacket.SUCCESS;

            C2SInventorySellItemCheckReqPacket inventorySellItemCheckReqPacket = new C2SInventorySellItemCheckReqPacket(packet);
            int itemPocketId = inventorySellItemCheckReqPacket.getItemPocketId();

            Pocket pocket = connection.getClient().getActivePlayer().getPocket();
            PlayerPocket playerPocket = playerPocketService.getItemAsPocket((long) itemPocketId, pocket);

            if(playerPocket == null) {
                status = S2CInventorySellAnswerPacket.NO_ITEM;

                S2CInventorySellItemCheckAnswerPacket inventorySellItemCheckAnswerPacket = new S2CInventorySellItemCheckAnswerPacket(status);
                connection.sendTCP(inventorySellItemCheckAnswerPacket);
                break;
            }

            int sellPrice = playerPocketService.getSellPrice(playerPocket);

            S2CInventorySellItemCheckAnswerPacket inventorySellItemCheckAnswerPacket = new S2CInventorySellItemCheckAnswerPacket(status);
            connection.sendTCP(inventorySellItemCheckAnswerPacket);

            S2CInventorySellItemAnswerPacket inventorySellItemAnswerPacket = new S2CInventorySellItemAnswerPacket((char) playerPocket.getItemCount().intValue(), itemPocketId);
            connection.sendTCP(inventorySellItemAnswerPacket);

            playerPocketService.remove(playerPocket.getId());
            pocket = pocketService.decrementPocketBelongings(connection.getClient().getActivePlayer().getPocket());
            connection.getClient().getActivePlayer().setPocket(pocket);

            Player player = connection.getClient().getActivePlayer();
            player = playerService.updateMoney(player, sellPrice);

            S2CShopMoneyAnswerPacket shopMoneyAnswerPacket = new S2CShopMoneyAnswerPacket(player);
            connection.sendTCP(shopMoneyAnswerPacket);

            connection.getClient().setActivePlayer(player);
        } break;
        }
    }

    public void handleInventoryWearClothPacket(Connection connection, Packet packet) {
        C2SInventoryWearClothReqPacket inventoryWearClothReqPacket = new C2SInventoryWearClothReqPacket(packet);

        Player player = connection.getClient().getActivePlayer();
        ClothEquipment clothEquipment = player.getClothEquipment();

        clothEquipmentService.updateCloths(clothEquipment, inventoryWearClothReqPacket);
        player.setClothEquipment(clothEquipment);

        player = playerService.save(player);
        connection.getClient().setActivePlayer(player);

        StatusPointsAddedDto statusPointsAddedDto = clothEquipmentService.getStatusPointsFromCloths(player);

        S2CInventoryWearClothAnswerPacket inventoryWearClothAnswerPacket = new S2CInventoryWearClothAnswerPacket((char) 0, inventoryWearClothReqPacket, player, statusPointsAddedDto);
        connection.sendTCP(inventoryWearClothAnswerPacket);
    }

    public void handleInventoryWearQuickPacket(Connection connection, Packet packet) {
        C2SInventoryWearQuickReqPacket inventoryWearQuickReqPacket = new C2SInventoryWearQuickReqPacket(packet);

        Player player = connection.getClient().getActivePlayer();
        QuickSlotEquipment quickSlotEquipment = player.getQuickSlotEquipment();

        quickSlotEquipmentService.updateQuickSlots(quickSlotEquipment, inventoryWearQuickReqPacket.getQuickSlotList());
        player.setQuickSlotEquipment(quickSlotEquipment);

        player = playerService.save(player);
        connection.getClient().setActivePlayer(player);

        S2CInventoryWearQuickAnswerPacket inventoryWearQuickAnswerPacket = new S2CInventoryWearQuickAnswerPacket(inventoryWearQuickReqPacket.getQuickSlotList());
        connection.sendTCP(inventoryWearQuickAnswerPacket);
    }

    public void handleInventoryItemTimeExpiredPacket(Connection connection, Packet packet) {
        C2SInventoryItemTimeExpiredReqPacket inventoryItemTimeExpiredReqPacket = new C2SInventoryItemTimeExpiredReqPacket(packet);

        Pocket pocket = connection.getClient().getActivePlayer().getPocket();

        playerPocketService.remove((long) inventoryItemTimeExpiredReqPacket.getItemPocketId());
        pocket = pocketService.decrementPocketBelongings(pocket);

        connection.getClient().getActivePlayer().setPocket(pocket);

        S2CInventoryItemRemoveAnswerPacket inventoryItemRemoveAnswerPacket = new S2CInventoryItemRemoveAnswerPacket(inventoryItemTimeExpiredReqPacket.getItemPocketId());
        connection.sendTCP(inventoryItemRemoveAnswerPacket);
    }

    public void handleShopMoneyRequestPacket(Connection connection, Packet packet) {
        Player player = playerService.findByIdFetched(connection.getClient().getActivePlayer().getId());
        connection.getClient().setActivePlayer(player);

        S2CShopMoneyAnswerPacket shopMoneyAnswerPacket = new S2CShopMoneyAnswerPacket(player);
        connection.sendTCP(shopMoneyAnswerPacket);
    }

    public void handleShopBuyRequestPacket(Connection connection, Packet packet) {
        C2SShopBuyPacket shopBuyPacket = new C2SShopBuyPacket(packet);

        Map<Integer, Byte> itemList = shopBuyPacket.getItemList();

        Map<Product, Byte> productList = productService.findProductsByItemList(itemList);

        Player player = connection.getClient().getActivePlayer();

        int gold = player.getGold();
        int costs = productList.keySet()
            .stream()
            .mapToInt(Product::getPrice0)
            .sum();

        int result = gold - costs;

        List<PlayerPocket> playerPocketList = new ArrayList<>();

        if (result >= 0) {
            for (Map.Entry<Product, Byte> data : productList.entrySet()) {
                Product product = data.getKey();
                byte option = data.getValue();

                // prevent user from buying pet till it'simplemented
                if (product.getCategory().equals(EItemCategory.PET_CHAR.getName())) {
                    result += product.getPrice0();
                    continue;
                }

                if (!product.getCategory().equals(EItemCategory.CHAR.getName())) {
                    if (product.getCategory().equals(EItemCategory.HOUSE.getName())) {

                        ItemHouse itemHouse = homeService.findItemHouseByItemIndex(product.getItem0());
                        AccountHome accountHome = homeService.findAccountHomeByAccountId(connection.getClient().getAccount().getId());

                        accountHome.setLevel(itemHouse.getLevel());
                        accountHome = homeService.save(accountHome);

                        S2CHomeDataPacket homeDataPacket = new S2CHomeDataPacket(accountHome);
                        connection.sendTCP(homeDataPacket);
                    } else {
                        // gold back
                        if (product.getGoldBack() != 0)
                            result += product.getGoldBack();

                        Pocket pocket = connection.getClient().getActivePlayer().getPocket();

                        if (product.getItem1() != 0) {

                            List<Integer> itemPartList = new ArrayList<>();

                            // use reflection to get indexes of item0-9
                            ReflectionUtils.doWithFields(product.getClass(), field -> {

                                    if (field.getName().startsWith("item")) {

                                        field.setAccessible(true);

                                        Integer itemIndex = (Integer) field.get(product);
                                        if (itemIndex != 0) {
                                            itemPartList.add(itemIndex);
                                        }

                                        field.setAccessible(false);
                                    }
                                });

                            // case if set has player included, items are transferred to the new player
                            if (product.getForPlayer() != -1) {

                                Player newPlayer = productService.createNewPlayer(connection.getClient().getAccount(), product.getForPlayer());
                                Pocket newPlayerPocket = pocketService.findById(newPlayer.getPocket().getId());

                                for (Integer itemIndex : itemPartList) {

                                    PlayerPocket playerPocket = new PlayerPocket();
                                    playerPocket.setCategory(product.getCategory());
                                    playerPocket.setItemIndex(itemIndex);
                                    playerPocket.setUseType(product.getUseType());
                                    playerPocket.setItemCount(1);
                                    playerPocket.setPocket(newPlayerPocket);

                                    playerPocketService.save(playerPocket);
                                    newPlayerPocket = pocketService.incrementPocketBelongings(newPlayerPocket);
                                }
                            }
                            else {
                                for (Integer itemIndex : itemPartList) {

                                    PlayerPocket playerPocket = new PlayerPocket();
                                    playerPocket.setCategory(product.getCategory());
                                    playerPocket.setItemIndex(itemIndex);
                                    playerPocket.setUseType(product.getUseType());
                                    playerPocket.setItemCount(1);
                                    playerPocket.setPocket(pocket);

                                    playerPocket = playerPocketService.save(playerPocket);
                                    pocket = pocketService.incrementPocketBelongings(pocket);

                                    // add item to result
                                    playerPocketList.add(playerPocket);
                                }
                            }
                        } else {
                            PlayerPocket playerPocket = playerPocketService.getItemAsPocketByItemIndexAndPocket(product.getItem0(), player.getPocket());
                            int existingItemCount = 0;
                            boolean existingItem = false;

                            if (playerPocket != null && !playerPocket.getUseType().equals("N/A")) {
                                existingItemCount = playerPocket.getItemCount();
                                existingItem = true;
                            } else {
                                playerPocket = new PlayerPocket();
                            }

                            playerPocket.setCategory(product.getCategory());
                            playerPocket.setItemIndex(product.getItem0());
                            playerPocket.setUseType(product.getUseType());

                            if (option == 0)
                                playerPocket.setItemCount(product.getUse0() == 0 ? 1 : product.getUse0());
                            else if (option == 1)
                                playerPocket.setItemCount(product.getUse1());
                            else if (option == 2)
                                playerPocket.setItemCount(product.getUse2());

                            playerPocket.setItemCount(playerPocket.getItemCount() + existingItemCount);

                            if (playerPocket.getUseType().equalsIgnoreCase(EItemUseType.TIME.getName())) {
                                Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                                cal.add(Calendar.DAY_OF_MONTH, playerPocket.getItemCount());

                                playerPocket.setCreated(cal.getTime());
                            }
                            playerPocket.setPocket(pocket);

                            playerPocket = playerPocketService.save(playerPocket);
                            if (!existingItem)
                                pocket = pocketService.incrementPocketBelongings(pocket);

                            // add item to result
                            playerPocketList.add(playerPocket);
                        }

                        connection.getClient().getActivePlayer().setPocket(pocket);
                    }
                }
                else {
                    productService.createNewPlayer(connection.getClient().getAccount(), product.getForPlayer());
                }
            }

            S2CShopBuyPacket shopBuyPacketAnswer = new S2CShopBuyPacket(S2CShopBuyPacket.SUCCESS, playerPocketList);
            connection.sendTCP(shopBuyPacketAnswer);

            player = playerService.setMoney(player, result);

            S2CShopMoneyAnswerPacket shopMoneyAnswerPacket = new S2CShopMoneyAnswerPacket(player);
            connection.sendTCP(shopMoneyAnswerPacket);

            connection.getClient().setActivePlayer(player);
        } else {
            S2CShopBuyPacket shopBuyPacketAnswer = new S2CShopBuyPacket(S2CShopBuyPacket.NEED_MORE_GOLD, null);
            connection.sendTCP(shopBuyPacketAnswer);
        }
    }

    public void handleShopRequestDataPackets(Connection connection, Packet packet) {
        switch (packet.getPacketId()) {
        case PacketID.C2SShopRequestDataPrepare: {
            C2SShopRequestDataPreparePacket shopRequestDataPreparePacket = new C2SShopRequestDataPreparePacket(packet);
            byte category = shopRequestDataPreparePacket.getCategory();
            byte part = shopRequestDataPreparePacket.getPart();
            byte player = shopRequestDataPreparePacket.getPlayer();

            int productListSize = productService.getProductListSize(category, part, player);

            S2CShopAnswerDataPreparePacket shopAnswerDataPreparePacket = new S2CShopAnswerDataPreparePacket(category, part, player, productListSize);
            connection.sendTCP(shopAnswerDataPreparePacket);
        } break;
        case PacketID.C2SShopRequestData: {
            C2SShopRequestDataPacket shopRequestDataPacket = new C2SShopRequestDataPacket(packet);

            byte category = shopRequestDataPacket.getCategory();
            byte part = shopRequestDataPacket.getPart();
            byte player = shopRequestDataPacket.getPlayer();
            int page = BitKit.fromUnsignedInt(shopRequestDataPacket.getPage());

            List<Product> productList = productService.getProductList(category, part, player, page);

            S2CShopAnswerDataPacket shopAnswerDataPacket = new S2CShopAnswerDataPacket(productList.size(), productList);
            connection.sendTCP(shopAnswerDataPacket);
        } break;
        }
    }

    public void handlePlayerStatusPointChangePacket(Connection connection, Packet packet) {
        C2SPlayerStatusPointChangePacket playerStatusPointChangePacket = new C2SPlayerStatusPointChangePacket(packet);

        Player player = connection.getClient().getActivePlayer();

        // we can't change; attributes should be server sided
        if (player.getStatusPoints() == 0) {
            S2CPlayerStatusPointChangePacket playerStatusPointChangeAnswerPacket = new S2CPlayerStatusPointChangePacket(player, new StatusPointsAddedDto());
            connection.sendTCP(playerStatusPointChangeAnswerPacket);
        }
        else if (player.getStatusPoints() > 0 && playerStatusPointChangePacket.getStatusPoints() >= 0) {
            if (playerService.isStatusPointHack(playerStatusPointChangePacket, player)) {
                S2CPlayerStatusPointChangePacket playerStatusPointChangeAnswerPacket = new S2CPlayerStatusPointChangePacket(player, new StatusPointsAddedDto());
                connection.sendTCP(playerStatusPointChangeAnswerPacket);
            } else {
                player.setStrength(playerStatusPointChangePacket.getStrength());
                player.setStamina(playerStatusPointChangePacket.getStamina());
                player.setDexterity(playerStatusPointChangePacket.getDexterity());
                player.setWillpower(playerStatusPointChangePacket.getWillpower());
                player.setStatusPoints(playerStatusPointChangePacket.getStatusPoints());

                player = playerService.save(player);

                connection.getClient().setActivePlayer(player);

                StatusPointsAddedDto statusPointsAddedDto = clothEquipmentService.getStatusPointsFromCloths(player);

                S2CPlayerStatusPointChangePacket playerStatusPointChangeAnswerPacket = new S2CPlayerStatusPointChangePacket(player, statusPointsAddedDto);
                connection.sendTCP(playerStatusPointChangeAnswerPacket);
            }
        }
    }

    public void handleChallengeProgressRequestPacket(Connection connection, Packet packet) {
        List<ChallengeProgress> challengeProgressList = challengeService.findAllByPlayerIdFetched(connection.getClient().getActivePlayer().getId());

        S2CChallengeProgressAnswerPacket challengeProgressAnswerPacket = new S2CChallengeProgressAnswerPacket(challengeProgressList);
        connection.sendTCP(challengeProgressAnswerPacket);
    }

    public void handleTutorialProgressRequestPacket(Connection connection, Packet packet) {
        List<TutorialProgress> tutorialProgressList = tutorialService.findAllByPlayerIdFetched(connection.getClient().getActivePlayer().getId());

        S2CTutorialProgressAnswerPacket tutorialProgressAnswerPacket = new S2CTutorialProgressAnswerPacket(tutorialProgressList);
        connection.sendTCP(tutorialProgressAnswerPacket);
    }

    public void handleChallengeBeginRequestPacket(Connection connection, Packet packet) {
        C2SChallengeBeginRequestPacket challengeBeginRequestPacket = new C2SChallengeBeginRequestPacket(packet);
        int challengeId = challengeBeginRequestPacket.getChallengeId();

        Challenge currentChallenge = challengeService.findChallengeByChallengeIndex(challengeId);

        if (currentChallenge.getGameMode() == GameMode.BASIC)
            connection.getClient().setActiveChallengeGame(new ChallengeBasicGame(challengeId));
        else if (currentChallenge.getGameMode() == GameMode.BATTLE)
            connection.getClient().setActiveChallengeGame(new ChallengeBattleGame(challengeId));

        Packet answer = new Packet(PacketID.C2STutorialBegin);
        answer.write((char) 1);
        connection.sendTCP(answer);
    }

    public void handleChallengeHpPacket(Connection connection, Packet packet) {
        C2SChallengeHpPacket challengeHpPacket = new C2SChallengeHpPacket(packet);

        if (connection.getClient().getActiveChallengeGame() instanceof ChallengeBattleGame) {

            ((ChallengeBattleGame) connection.getClient().getActiveChallengeGame()).setMaxPlayerHp(challengeHpPacket.getPlayerHp());
            ((ChallengeBattleGame) connection.getClient().getActiveChallengeGame()).setMaxNpcHp(challengeHpPacket.getNpcHp());
        }
    }

    public void handleChallengePointPacket(Connection connection, Packet packet) {
        C2SChallengePointPacket challengePointPacket = new C2SChallengePointPacket(packet);

        if (connection.getClient().getActiveChallengeGame() != null) {
            ((ChallengeBasicGame) connection.getClient().getActiveChallengeGame()).setPoints(challengePointPacket.getPointsPlayer(), challengePointPacket.getPointsNpc());

            if (connection.getClient().getActiveChallengeGame().isFinished()) {
                boolean win = ((ChallengeBasicGame) connection.getClient().getActiveChallengeGame()).getSetsPlayer() == 2;
                challengeService.finishGame(connection, win);

                connection.getClient().setActiveChallengeGame(null);
            }
        }
    }

    public void handleChallengeDamagePacket(Connection connection, Packet packet) {
        C2SChallengeDamagePacket challengeDamagePacket = new C2SChallengeDamagePacket(packet);

        if (connection.getClient().getActiveChallengeGame() != null) {
            ((ChallengeBattleGame) connection.getClient().getActiveChallengeGame()).setHp(challengeDamagePacket.getPlayer(), challengeDamagePacket.getDmg());

            if (connection.getClient().getActiveChallengeGame().isFinished()) {
                boolean win = ((ChallengeBattleGame) connection.getClient().getActiveChallengeGame()).getPlayerHp() > 0;
                challengeService.finishGame(connection, win);

                connection.getClient().setActiveChallengeGame(null);
            }
        }
    }

    public void handleQuickSlotUseRequest(Connection connection, Packet packet) {
        C2SQuickSlotUseRequestPacket quickSlotUseRequestPacket = new C2SQuickSlotUseRequestPacket(packet);

        Player player = connection.getClient().getActivePlayer();
        Pocket pocket = player.getPocket();

        PlayerPocket playerPocket = playerPocketService.getItemAsPocket((long) quickSlotUseRequestPacket.getQuickSlotId(), pocket);
        int itemCount = playerPocket.getItemCount() - 1;

        if (itemCount <= 0) {

            playerPocketService.remove(playerPocket.getId());
            pocket = pocketService.decrementPocketBelongings(pocket);
            connection.getClient().getActivePlayer().setPocket(pocket);

            QuickSlotEquipment quickSlotEquipment = player.getQuickSlotEquipment();
            quickSlotEquipmentService.updateQuickSlots(quickSlotEquipment, quickSlotUseRequestPacket.getQuickSlotId());
            player.setQuickSlotEquipment(quickSlotEquipment);

            player = playerService.save(player);
            connection.getClient().setActivePlayer(player);

            S2CInventoryItemRemoveAnswerPacket inventoryItemRemoveAnswerPacket = new S2CInventoryItemRemoveAnswerPacket(quickSlotUseRequestPacket.getQuickSlotId());
            connection.sendTCP(inventoryItemRemoveAnswerPacket);
        } else {
            playerPocket.setItemCount(itemCount);
            playerPocketService.save(playerPocket);
        }
    }

    public void handleChallengeSetPacket(Connection connection, Packet packet) {
        // empty..
    }

    public void handleTutorialBeginPacket(Connection connection, Packet packet) {
        C2STutorialBeginRequestPacket tutorialBeginRequestPacket = new C2STutorialBeginRequestPacket(packet);
        int tutorialId = tutorialBeginRequestPacket.getTutorialId();

        connection.getClient().setActiveTutorialGame(new TutorialGame(tutorialId));

        Packet answer = new Packet(PacketID.C2STutorialBegin);
        answer.write((char) 1);
        connection.sendTCP(answer);
    }

    public void handleTutorialEndPacket(Connection connection, Packet packet) {
        C2STutorialEndPacket tutorialEndPacket = new C2STutorialEndPacket(packet);
        connection.getClient().getActiveTutorialGame().finishTutorial();

        tutorialService.finishGame(connection);

        connection.getClient().setActiveTutorialGame(null);
    }

    public void handleLobbyUserListReqPacket(Connection connection, Packet packet) {
        C2SLobbyUserListRequestPacket lobbyUserListRequestPacket = new C2SLobbyUserListRequestPacket(packet);
        byte page = lobbyUserListRequestPacket.getPage();
        byte clientLobbyCurrentPlayerListPage = connection.getClient().getLobbyCurrentPlayerListPage();
        boolean shouldJustRefresh = lobbyUserListRequestPacket.getRefresh() == 0 & page == 1;
        boolean wantsToGoBackOnNegativePage = page == -1 && clientLobbyCurrentPlayerListPage == 1;
        if (wantsToGoBackOnNegativePage || shouldJustRefresh) {
            page = 0;
        }

        clientLobbyCurrentPlayerListPage += page;
        connection.getClient().setLobbyCurrentPlayerListPage(clientLobbyCurrentPlayerListPage);
        List<Player> lobbyPlayerList = this.gameHandler.getPlayersInLobby().stream()
            .skip(clientLobbyCurrentPlayerListPage == 1 ? 0 : (clientLobbyCurrentPlayerListPage * 10) - 10)
            .limit(10)
            .collect(Collectors.toList());

        S2CLobbyUserListAnswerPacket lobbyUserListAnswerPacket = new S2CLobbyUserListAnswerPacket(lobbyPlayerList);
        connection.sendTCP(lobbyUserListAnswerPacket);
    }

    public void handleChatMessagePackets(Connection connection, Packet packet) {
        switch (packet.getPacketId()) {
        case PacketID.C2SChatLobbyReq: {
            C2SChatLobbyReqPacket chatLobbyReqPacket = new C2SChatLobbyReqPacket(packet);
            S2CChatLobbyAnswerPacket chatLobbyAnswerPacket = new S2CChatLobbyAnswerPacket(chatLobbyReqPacket.getUnk(), connection.getClient().getActivePlayer().getName(), chatLobbyReqPacket.getMessage());

            this.getGameHandler().getClientList().forEach(cl -> cl.getConnection().sendTCP(chatLobbyAnswerPacket));
        } break;
        case PacketID.C2SChatRoomReq: {
            C2SChatRoomReqPacket chatRoomReqPacket = new C2SChatRoomReqPacket(packet);
            S2CChatRoomAnswerPacket chatRoomAnswerPacket = new S2CChatRoomAnswerPacket(chatRoomReqPacket.getType(), connection.getClient().getActivePlayer().getName(), chatRoomReqPacket.getMessage());

            this.gameHandler.getClientsInRoom(connection.getClient().getActiveRoom().getRoomId()).forEach(c -> c.getConnection().sendTCP(chatRoomAnswerPacket));
        } break;
        case PacketID.C2SWhisperReq: {
            C2SWhisperReqPacket whisperReqPacket = new C2SWhisperReqPacket(packet);
            S2CWhisperAnswerPacket whisperAnswerPacket = new S2CWhisperAnswerPacket(connection.getClient().getActivePlayer().getName(), whisperReqPacket.getReceiverName(), whisperReqPacket.getMessage());

            this.getGameHandler().getClientList().stream()
                .filter(cl -> cl.getActivePlayer().getName().equals(whisperReqPacket.getReceiverName()))
                .findAny()
                .ifPresent(cl -> cl.getConnection().sendTCP(whisperAnswerPacket));

            connection.sendTCP(whisperAnswerPacket);
        } break;
        }
    }

    public void handleLobbyJoinLeave(Connection connection, boolean joined) {
        connection.getClient().setInLobby(joined);
        if (joined && connection.getClient().getActiveRoom() != null) {
            handleRoomPlayerChanges(connection);
        }

        this.refreshLobbyPlayerListForAllClients();
    }

    public void handleEmblemListRequestPacket(Connection connection, Packet packet) {
        // empty..
    }

    public void handleOpenGachaRequestPacket(Connection connection, Packet packet) {
        C2SOpenGachaReqPacket openGachaReqPacket = new C2SOpenGachaReqPacket(packet);
        long playerPocketId = openGachaReqPacket.getPlayerPocketId();
        int productIndex = openGachaReqPacket.getProductIndex();

        List<PlayerPocket> playerPocketList = lotteryService.drawLottery(connection, playerPocketId, productIndex);

        S2COpenGachaAnswerPacket openGachaAnswerPacket = new S2COpenGachaAnswerPacket(playerPocketList);
        connection.sendTCP(openGachaAnswerPacket);
    }

    public void handleRoomCreateRequestPacket(Connection connection, Packet packet) {
        C2SRoomCreateRequestPacket roomCreateRequestPacket = new C2SRoomCreateRequestPacket(packet);

        Room room = new Room();
        room.setRoomId((short) this.gameHandler.getRoomList().size());
        room.setRoomName(roomCreateRequestPacket.getRoomName());
        room.setAllowBattlemon(roomCreateRequestPacket.getAllowBattlemon());
        room.setMode(roomCreateRequestPacket.getMode());
        room.setRule(roomCreateRequestPacket.getRule());
        room.setPlayers(roomCreateRequestPacket.getPlayers());
        room.setPrivate(roomCreateRequestPacket.isPrivate());
        room.setUnk1(roomCreateRequestPacket.getUnk1());
        room.setSkillFree(roomCreateRequestPacket.isSkillFree());
        room.setQuickSlot(roomCreateRequestPacket.isQuickSlot());
        room.setLevel(connection.getClient().getActivePlayer().getLevel());
        room.setLevelRange(roomCreateRequestPacket.getLevelRange());
        room.setBettingType(roomCreateRequestPacket.getBettingType());
        room.setBettingAmount(roomCreateRequestPacket.getBettingAmount());
        room.setBall(roomCreateRequestPacket.getBall());
        room.setMap((byte) 1);

        internalHandleRoomCreate(connection, room);
    }

    public void handleRoomCreateQuickRequestPacket(Connection connection, Packet packet) {
        C2SRoomCreateQuickRequestPacket roomQuickCreateRequestPacket = new C2SRoomCreateQuickRequestPacket(packet);
        Player player = connection.getClient().getActivePlayer();
        byte playerSize = roomQuickCreateRequestPacket.getPlayers();

        Room room = new Room();
        room.setRoomId((short) this.gameHandler.getRoomList().size());
        room.setRoomName(String.format("%s's room", player.getName()));
        room.setAllowBattlemon(roomQuickCreateRequestPacket.getAllowBattlemon());
        room.setMode(roomQuickCreateRequestPacket.getMode());
        room.setRule((byte) 0);
        room.setPlayers(playerSize == 0 ? 2 : playerSize);
        room.setPrivate(false);
        room.setUnk1((byte) 0);
        room.setSkillFree(false);
        room.setQuickSlot(false);
        room.setLevel(player.getLevel());
        room.setLevelRange((byte) -1);
        room.setBettingType('0');
        room.setBettingAmount(0);
        room.setBall(1);
        room.setMap((byte) 1);

        internalHandleRoomCreate(connection, room);
    }

    public void handleRoomNameChangePacket(Connection connection, Packet packet) {
        C2SRoomNameChangeRequestPacket changeRoomNameRequestPacket = new C2SRoomNameChangeRequestPacket(packet);
        Room room = connection.getClient().getActiveRoom();
        room.setRoomName(changeRoomNameRequestPacket.getRoomName());
        S2CRoomInformationPacket roomInformationPacket = new S2CRoomInformationPacket(room);
        connection.sendTCP(roomInformationPacket);
        this.gameHandler.getClientsInRoom(room.getRoomId()).forEach(c -> c.getConnection().sendTCP(roomInformationPacket));
        this.refreshLobbyRoomListForAllClients(connection, getRoomMode(room));
    }

    public void handleGameModeChangePacket(Connection connection, Packet packet) {
        C2SRoomGameModeChangeRequestPacket changeRoomGameModeRequestPacket = new C2SRoomGameModeChangeRequestPacket(packet);
        Room room = connection.getClient().getActiveRoom();
        room.setMode(changeRoomGameModeRequestPacket.getMode());
        S2CRoomInformationPacket roomInformationPacket = new S2CRoomInformationPacket(room);
        connection.sendTCP(roomInformationPacket);
        this.gameHandler.getClientsInRoom(room.getRoomId()).forEach(c -> c.getConnection().sendTCP(roomInformationPacket));

        List<Room> rooms = this.gameHandler.getRoomList();
        this.gameHandler.getClientsInLobby().forEach(c -> {
            boolean isActivePlayer = c.getActivePlayer().getId().equals(connection.getClient().getActivePlayer().getId());
            if (isActivePlayer)
                return;

            int clientRoomModeFilter = c.getLobbyGameModeTabFilter();
            List<Room> filteredRoomListByTab = clientRoomModeFilter == GameMode.ALL ? rooms :
                    rooms.stream().filter(x -> getRoomMode(x) == clientRoomModeFilter).collect(Collectors.toList());
            S2CRoomListAnswerPacket roomListAnswerPacket = new S2CRoomListAnswerPacket(filteredRoomListByTab);
            c.getConnection().sendTCP(roomListAnswerPacket);
        });
    }

    public void handleRoomIsPrivateChangePacket(Connection connection, Packet packet) {
        C2SRoomIsPrivateChangeRequestPacket changeRoomIsPrivateRequestPacket = new C2SRoomIsPrivateChangeRequestPacket(packet);
        String password = changeRoomIsPrivateRequestPacket.getPassword();
        Room room = connection.getClient().getActiveRoom();
        if (StringUtils.isEmpty(password)) {
            room.setPassword(null);
            room.setPrivate(false);
        }
        else {
            room.setPassword(password);
            room.setPrivate(true);
        }

        this.updateRoomForAllPlayersInMultiplayer(connection, room);
    }

    public void handleRoomLevelRangeChangePacket(Connection connection, Packet packet) {
        C2SRoomLevelRangeChangeRequestPacket changeRoomLevelRangeRequestPacket = new C2SRoomLevelRangeChangeRequestPacket(packet);
        Room room = connection.getClient().getActiveRoom();
        room.setLevelRange(changeRoomLevelRangeRequestPacket.getLevelRange());
        this.updateRoomForAllPlayersInMultiplayer(connection, room);
    }

    public void handleRoomSkillFreeChangePacket(Connection connection, Packet packet) {
        C2SRoomSkillFreeChangeRequestPacket changeRoomSkillFreeRequestPacket = new C2SRoomSkillFreeChangeRequestPacket(packet);
        Room room = connection.getClient().getActiveRoom();
        room.setSkillFree(changeRoomSkillFreeRequestPacket.isSkillFree());
        this.updateRoomForAllPlayersInMultiplayer(connection, room);
    }

    public void handleRoomAllowBattlemonChangePacket(Connection connection, Packet packet) {
        C2SRoomAllowBattlemonChangeRequestPacket changeRoomAllowBattlemonRequestPacket = new C2SRoomAllowBattlemonChangeRequestPacket(packet);
        Room room = connection.getClient().getActiveRoom();

        byte allowBattlemon = changeRoomAllowBattlemonRequestPacket.getAllowBattlemon() == 1 ? (byte) 2 : (byte) 0;
        room.setAllowBattlemon(allowBattlemon);
        this.updateRoomForAllPlayersInMultiplayer(connection, room);
    }

    public void handleRoomQuickSlotChangePacket(Connection connection, Packet packet) {
        C2SRoomQuickSlotChangeRequestPacket changeRoomQuickSlotRequestPacket = new C2SRoomQuickSlotChangeRequestPacket(packet);
        Room room = connection.getClient().getActiveRoom();
        room.setQuickSlot(changeRoomQuickSlotRequestPacket.isQuickSlot());
        this.updateRoomForAllPlayersInMultiplayer(connection, room);
    }

    public void handleRoomJoinRequestPacket(Connection connection, Packet packet) {
        C2SRoomJoinRequestPacket roomJoinRequestPacket = new C2SRoomJoinRequestPacket(packet);

        Room room = this.gameHandler.getRoomList().stream()
                .filter(r -> r.getRoomId() == roomJoinRequestPacket.getRoomId())
                .findAny()
                .get();

        boolean anyPositionAvailable = room.getPositions().stream().anyMatch(x -> x == RoomPositionState.Free);
        if (!anyPositionAvailable) {
            S2CRoomJoinAnswerPacket roomJoinAnswerPacket = new S2CRoomJoinAnswerPacket((char) -1, (byte) 0, (byte) 0, (byte) 0);
            connection.sendTCP(roomJoinAnswerPacket);
            return;
        }

        Optional<Short> num = room.getPositions().stream().filter(x -> x == RoomPositionState.Free).findFirst();
        int newPosition = room.getPositions().indexOf(num.get());
        room.getPositions().set(newPosition, RoomPositionState.InUse);

        RoomPlayer roomPlayer = new RoomPlayer();
        roomPlayer.setPlayer(connection.getClient().getActivePlayer());
        roomPlayer.setClothEquipment(clothEquipmentService.findClothEquipmentById(roomPlayer.getPlayer().getClothEquipment().getId()));
        roomPlayer.setStatusPointsAddedDto(clothEquipmentService.getStatusPointsFromCloths(roomPlayer.getPlayer()));
        roomPlayer.setPosition((short) newPosition);
        roomPlayer.setMaster(false);
        roomPlayer.setFitting(false);
        room.getRoomPlayerList().add(roomPlayer);

        connection.getClient().setActiveRoom(room);
        connection.getClient().setInLobby(false);

        S2CRoomJoinAnswerPacket roomJoinAnswerPacket = new S2CRoomJoinAnswerPacket((char) 0, (byte) 0, (byte) 0, (byte) 0);
        connection.sendTCP(roomJoinAnswerPacket);

        S2CRoomInformationPacket roomInformationPacket = new S2CRoomInformationPacket(room);
        connection.sendTCP(roomInformationPacket);

        List<Short> positions = room.getPositions();
        for (int i = 0; i < positions.size(); i++) {
            short positionState = room.getPositions().get(i);
            if (positionState == RoomPositionState.Locked) {
                S2CRoomSlotCloseAnswerPacket roomSlotCloseAnswerPacket = new S2CRoomSlotCloseAnswerPacket((byte) i, true);
                connection.sendTCP(roomSlotCloseAnswerPacket);
            }
        }

        S2CRoomPlayerInformationPacket roomPlayerInformationPacket = new S2CRoomPlayerInformationPacket(room.getRoomPlayerList());
        this.gameHandler.getClientsInRoom(roomJoinRequestPacket.getRoomId()).forEach(c -> c.getConnection().sendTCP(roomPlayerInformationPacket));
        this.updateRoomForAllPlayersInMultiplayer(connection, room);
        this.refreshLobbyPlayerListForAllClients();
    }

    public void handleRoomLeaveRequestPacket(Connection connection, Packet packet) {
        handleRoomPlayerChanges(connection);
        Packet answerPacket = new Packet((char) PacketID.S2CRoomLeaveAnswer);
        answerPacket.write(0);
        connection.sendTCP(answerPacket);
    }

    public void handleRoomReadyChangeRequestPacket(Connection connection, Packet packet) {
        C2SRoomReadyChangeRequestPacket roomReadyChangeRequestPacket = new C2SRoomReadyChangeRequestPacket(packet);

        connection.getClient().getActiveRoom().getRoomPlayerList().stream()
                .filter(rp -> rp.getPlayer().getId().equals(connection.getClient().getActivePlayer().getId()))
                .findAny()
                .ifPresent(rp -> rp.setReady(roomReadyChangeRequestPacket.isReady()));

        S2CRoomPlayerInformationPacket roomPlayerInformationPacket = new S2CRoomPlayerInformationPacket(connection.getClient().getActiveRoom().getRoomPlayerList());
        this.gameHandler.getClientsInRoom(connection.getClient().getActiveRoom().getRoomId()).forEach(c -> c.getConnection().sendTCP(roomPlayerInformationPacket));
    }

    public void handleRoomMapChangeRequestPacket(Connection connection, Packet packet) {
        C2SRoomMapChangeRequestPacket roomMapChangeRequestPacket = new C2SRoomMapChangeRequestPacket(packet);
        connection.getClient().getActiveRoom().setMap(roomMapChangeRequestPacket.getMap());
        Room room = connection.getClient().getActiveRoom();
        S2CRoomMapChangeAnswerPacket roomMapChangeAnswerPacket = new S2CRoomMapChangeAnswerPacket(roomMapChangeRequestPacket.getMap());
        this.gameHandler.getClientsInRoom(room.getRoomId()).forEach(c -> c.getConnection().sendTCP(roomMapChangeAnswerPacket));
        this.refreshLobbyRoomListForAllClients(connection, getRoomMode(room));
    }

    public void handleRoomPositionChangeRequestPacket(Connection connection, Packet packet) {
        C2SRoomPositionChangeRequestPacket roomPositionChangeRequestPacket = new C2SRoomPositionChangeRequestPacket(packet);
        short positionToClaim = roomPositionChangeRequestPacket.getPosition();

        Room room = connection.getClient().getActiveRoom();
        RoomPlayer requestingSlotChangePlayer = room.getRoomPlayerList().stream()
                .filter(rp -> rp.getPlayer().getId().equals(connection.getClient().getActivePlayer().getId()))
                .findAny()
                .orElse(null);

        if (requestingSlotChangePlayer != null) {
            short requestingSlotChangePlayerOldPosition = requestingSlotChangePlayer.getPosition();
            if (requestingSlotChangePlayerOldPosition == positionToClaim) {
                return;
            }

            boolean requestingSlotChangePlayerIsMaster = requestingSlotChangePlayer.isMaster();
            boolean playerIsSpectator = requestingSlotChangePlayerOldPosition > 3;
            boolean slotIsInUse = connection.getClient().getActiveRoom().getPositions().get(positionToClaim) == RoomPositionState.InUse;
            if (playerIsSpectator && slotIsInUse && !requestingSlotChangePlayerIsMaster) {
                S2CChatRoomAnswerPacket chatRoomAnswerPacket = new S2CChatRoomAnswerPacket((byte) 2, "Room", "You cannot claim this players slot");
                connection.sendTCP(chatRoomAnswerPacket);
                return;
            }

            boolean freeOldPosition = true;
            RoomPlayer playerInSlotToClaim = room.getRoomPlayerList().stream().filter(x -> x.getPosition() == positionToClaim).findAny().orElse(null);
            if (playerInSlotToClaim != null) {
                freeOldPosition = false;
                this.internalHandleRoomPositionChange(connection, playerInSlotToClaim, false,
                        playerInSlotToClaim.getPosition(), requestingSlotChangePlayerOldPosition);
            }

            this.internalHandleRoomPositionChange(connection, requestingSlotChangePlayer, freeOldPosition,
                    requestingSlotChangePlayerOldPosition, positionToClaim);
        }

        S2CRoomPlayerInformationPacket roomPlayerInformationPacket = new S2CRoomPlayerInformationPacket(connection.getClient().getActiveRoom().getRoomPlayerList());
        this.gameHandler.getClientsInRoom(connection.getClient().getActiveRoom().getRoomId()).forEach(c -> c.getConnection().sendTCP(roomPlayerInformationPacket));
        this.refreshLobbyRoomListForAllClients(connection, getRoomMode(connection.getClient().getActiveRoom()));
    }

    public void handleRoomSlotCloseRequestPacket(Connection connection, Packet packet) {
        C2SRoomSlotCloseRequestPacket roomSlotCloseRequestPacket = new C2SRoomSlotCloseRequestPacket(packet);
        boolean deactivate = roomSlotCloseRequestPacket.isDeactivate();

        byte slot = roomSlotCloseRequestPacket.getSlot();
        connection.getClient().getActiveRoom().getPositions().set(slot, deactivate ? RoomPositionState.Locked : RoomPositionState.Free);

        S2CRoomSlotCloseAnswerPacket roomSlotCloseAnswerPacket = new S2CRoomSlotCloseAnswerPacket(slot, deactivate);
        this.gameHandler.getClientsInRoom(connection.getClient().getActiveRoom().getRoomId()).forEach(c -> c.getConnection().sendTCP(roomSlotCloseAnswerPacket));
    }

    public void handleRoomStartGamePacket(Connection connection, Packet packet) {
        Packet roomStartGameAck = new Packet(PacketID.S2CRoomStartGameAck);
        roomStartGameAck.write((char) 0);

        Room room = connection.getClient().getActiveRoom();
        if (room.getStatus() == RoomStatus.StartingGame) {
            connection.sendTCP(roomStartGameAck);
            room.setStatus(RoomStatus.StartCancelled);
            return;
        }

        room.setStatus(RoomStatus.StartingGame);

        Thread thread = new Thread(() -> {
            int secondsToCount = 5;
            for (int i = 0; i < secondsToCount; i++) {
                Room threadRoom = connection.getClient().getActiveRoom();
                List<RoomPlayer> roomPlayerList = connection.getClient().getActiveRoom()
                    .getRoomPlayerList().stream().filter(x -> !x.isMaster()).collect(Collectors.toList());
                boolean allReady = roomPlayerList.stream().filter(x -> x.getPosition() < 4).allMatch(x -> x.isReady());
                if (!allReady || threadRoom.getStatus() == RoomStatus.StartCancelled) {
                    threadRoom.setStatus(RoomStatus.Idle);
                    Packet startGameCancelledPacket = new Packet(PacketID.S2CRoomStartGameCancelled);
                    startGameCancelledPacket.write((char) 0);
                    this.gameHandler.getClientsInRoom(room.getRoomId()).forEach(c -> c.getConnection().sendTCP(startGameCancelledPacket));
                    return;
                }

                String message = String.format("Game starting in %s...", secondsToCount - i);
                S2CChatRoomAnswerPacket chatRoomAnswerPacket = new S2CChatRoomAnswerPacket((byte) 2, "Room", message);
                this.gameHandler.getClientsInRoom(threadRoom.getRoomId()).forEach(c -> c.getConnection().sendTCP(chatRoomAnswerPacket));
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            Packet startGamePacket = new Packet(PacketID.S2CRoomStartGame);
            startGamePacket.write((char) 0);
            room.setStatus(RoomStatus.InGame);
            this.gameHandler.getClientsInRoom(connection.getClient().getActiveRoom().getRoomId())
                .forEach(c -> c.getConnection().sendTCP(startGamePacket));
        });
        thread.start();
        connection.sendTCP(roomStartGameAck);
    }

    public void handleGameAnimationReadyToSkipPacket(Connection connection, Packet packet) {
        Player player = connection.getClient().getActivePlayer();
        Room room = connection.getClient().getActiveRoom();
        room.getRoomPlayerList().stream()
            .filter(x -> x.getPlayer().getId().equals(player.getId()))
            .findFirst().ifPresent(rp -> rp.setGameAnimationSkipReady(true));
        boolean allPlayerCanSkipAnimation = connection.getClient().getActiveRoom()
                .getRoomPlayerList().stream().allMatch(x -> x.isGameAnimationSkipReady());

        if (allPlayerCanSkipAnimation) {
            Packet gameAnimationAllowSkipPacket = new Packet(PacketID.S2CGameAnimationAllowSkip);
            gameAnimationAllowSkipPacket.write((char) 0);
            this.gameHandler.getClientsInRoom(connection.getClient().getActiveRoom().getRoomId())
                .forEach(c -> c.getConnection().sendTCP(gameAnimationAllowSkipPacket));
        }
    }

    public  void handleGameAnimationSkipTriggeredPacket(Connection connection, Packet packet) {
        Packet gameAnimationSkipPacket = new Packet(PacketID.S2CGameAnimationSkip);
        gameAnimationSkipPacket.write((char) 0);
        this.gameHandler.getClientsInRoom(connection.getClient().getActiveRoom().getRoomId())
            .forEach(c -> c.getConnection().sendTCP(gameAnimationSkipPacket));

//        As of giving connection infos to clients 0x3EA is the packet you need, the structure of it is the host, port and player id's
//        Were the clients connect must be a "new server" because it's for the tcp relay logic
//        List<RoomPlayer> roomPlayerList = connection.getClient().getActiveRoom().getRoomPlayerList();
//        List<Long> playerIds = roomPlayerList.stream().map(x -> x.getPlayer().getId()).collect(Collectors.toList());
//
//        Packet gameTcpServerData = new Packet(PacketID.S2CGameTcpServerData);
//        gameTcpServerData.write("localhost");
//        gameTcpServerData.write(5894);
//        gameTcpServerData.write(playerIds);
//        connection.sendTCP(gameTcpServerData);
    }

    public void handleRoomListRequestPacket(Connection connection, Packet packet) {
        C2SRoomListRequestPacket roomListRequestPacket = new C2SRoomListRequestPacket(packet);
        char page = roomListRequestPacket.getPage();
        int roomType = roomListRequestPacket.getRoomTypeTab();
        int gameMode;
        switch (roomType) {
            case 256:
                gameMode = GameMode.GUARDIAN;
                break;
            case 192:
                gameMode = GameMode.BATTLE;
                break;
            case 48:
                gameMode = GameMode.BASIC;
                break;
            case 1536:
                gameMode = GameMode.BATTLEMON;
                break;
            default:
                gameMode = GameMode.ALL;
                break;
        }

        connection.getClient().setLobbyGameModeTabFilter(gameMode);
        int finalGameMode = gameMode;
        List<Room> roomList = this.gameHandler.getRoomList().stream()
                .filter(x -> finalGameMode == GameMode.ALL || getRoomMode(x) == finalGameMode)
                .skip(page == 0 ? 0 : (page * 5) - 5)
                .limit(5)
                .collect(Collectors.toList());

        S2CRoomListAnswerPacket roomListAnswerPacket = new S2CRoomListAnswerPacket(roomList);
        connection.sendTCP(roomListAnswerPacket);
    }

    public void handleDisconnectPacket(Connection connection, Packet packet) {

        // reset pocket
        List<PlayerPocket> playerPocketList = playerPocketService.getPlayerPocketItems(connection.getClient().getActivePlayer().getPocket());
        playerPocketList.forEach(pp -> {
            S2CInventoryItemRemoveAnswerPacket inventoryItemRemoveAnswerPacket = new S2CInventoryItemRemoveAnswerPacket((int) pp.getId().longValue());
            connection.sendTCP(inventoryItemRemoveAnswerPacket);
        });

        handleRoomPlayerChanges(connection);

        // reset status
        Account account = authenticationService.findAccountById(connection.getClient().getAccount().getId());
        account.setStatus((int) S2CLoginAnswerPacket.SUCCESS);
        authenticationService.updateAccount(account);

        S2CDisconnectAnswerPacket disconnectAnswerPacket = new S2CDisconnectAnswerPacket();
        connection.sendTCP(disconnectAnswerPacket);
    }

    public void handleDisconnected(Connection connection) {
        if (connection.getClient().getAccount() != null) {
            // reset status
            Account account = authenticationService.findAccountById(connection.getClient().getAccount().getId());
            account.setStatus((int) S2CLoginAnswerPacket.SUCCESS);
            authenticationService.updateAccount(account);

            handleRoomPlayerChanges(connection);
        }

        gameHandler.removeClient(connection.getClient());

        connection.setClient(null);
        connection.close();
    }

    public void handleUnknown(Connection connection, Packet packet) {
        Packet unknownAnswer = new Packet((char) (packet.getPacketId() + 1));
        if (unknownAnswer.getPacketId() == (char) 0x200E) {
            unknownAnswer.write((char) 1);
        }
        else {
            unknownAnswer.write((short) 0);
        }
        connection.sendTCP(unknownAnswer);
    }

    private void internalHandleRoomPositionChange(Connection connection, RoomPlayer roomPlayer, boolean freeOldPosition, short oldPosition, short newPosition) {
        Room room = connection.getClient().getActiveRoom();
        if (freeOldPosition) {
            room.getPositions().set(oldPosition, RoomPositionState.Free);
        }

        room.getPositions().set(newPosition, RoomPositionState.InUse);
        roomPlayer.setPosition(newPosition);
        S2CRoomPositionChangeAnswerPacket roomPositionChangePacket = new S2CRoomPositionChangeAnswerPacket((char) 0, oldPosition, newPosition);
        this.gameHandler.getClientsInRoom(room.getRoomId()).forEach(c -> c.getConnection().sendTCP(roomPositionChangePacket));
    }

    private void internalHandleRoomCreate(Connection connection, Room room) {
        room.getPositions().set(0, RoomPositionState.InUse);

        byte players = room.getPlayers();
        if (players == 2) {
            room.getPositions().set(2, RoomPositionState.Locked);
            room.getPositions().set(3, RoomPositionState.Locked);
        }
        RoomPlayer roomPlayer = new RoomPlayer();
        roomPlayer.setPlayer(connection.getClient().getActivePlayer());
        roomPlayer.setClothEquipment(clothEquipmentService.findClothEquipmentById(roomPlayer.getPlayer().getClothEquipment().getId()));
        roomPlayer.setStatusPointsAddedDto(clothEquipmentService.getStatusPointsFromCloths(roomPlayer.getPlayer()));
        roomPlayer.setPosition((short) 0);
        roomPlayer.setMaster(true);
        roomPlayer.setFitting(false);
        room.getRoomPlayerList().add(roomPlayer);

        this.gameHandler.getRoomList().add(room);
        connection.getClient().setActiveRoom(room);
        connection.getClient().setInLobby(false);

        S2CRoomCreateAnswerPacket roomCreateAnswerPacket = new S2CRoomCreateAnswerPacket((char) 0, (byte) 0, (byte) 0, (byte) 0);
        connection.sendTCP(roomCreateAnswerPacket);

        S2CRoomInformationPacket roomInformationPacket = new S2CRoomInformationPacket(room);
        connection.sendTCP(roomInformationPacket);

        S2CRoomPlayerInformationPacket roomPlayerInformationPacket = new S2CRoomPlayerInformationPacket(room.getRoomPlayerList());
        connection.sendTCP(roomPlayerInformationPacket);

        this.refreshLobbyRoomListForAllClients(connection, getRoomMode(room));
        this.refreshLobbyPlayerListForAllClients();
    }

    private void refreshLobbyRoomListForAllClients(Connection connection, int gameMode) {
        S2CRoomListAnswerPacket roomListAnswerPacket = new S2CRoomListAnswerPacket(this.gameHandler.getRoomList());
        this.gameHandler.getClientsInLobby().forEach(c -> {
            int clientRoomModeFilter = c.getLobbyGameModeTabFilter();
            boolean isNotActivePlayer = !c.getActivePlayer().getId().equals(connection.getClient().getActivePlayer().getId());
            boolean shouldRoomBeDisplayed = c.getLobbyGameModeTabFilter() == GameMode.ALL ? true : clientRoomModeFilter == gameMode;
            if (isNotActivePlayer && shouldRoomBeDisplayed)
                c.getConnection().sendTCP(roomListAnswerPacket);
        });
    }

    private void refreshLobbyPlayerListForAllClients() {
        this.gameHandler.getClientsInLobby().forEach(c -> {
            byte currentPage = c.getLobbyCurrentPlayerListPage();
            List<Player> lobbyPlayerList = this.gameHandler.getPlayersInLobby().stream()
                    .skip(currentPage == 1 ? 0 : (currentPage * 10) - 10)
                    .limit(10)
                    .collect(Collectors.toList());
            S2CLobbyUserListAnswerPacket lobbyUserListAnswerPacket = new S2CLobbyUserListAnswerPacket(lobbyPlayerList);
            c.getConnection().sendTCP(lobbyUserListAnswerPacket);
        });
    }

    private void handleRoomPlayerChanges(Connection connection) {
        if (connection.getClient().getActiveRoom() != null) {
            List<RoomPlayer> roomPlayerList = connection.getClient().getActiveRoom().getRoomPlayerList();
            Optional<RoomPlayer> roomPlayer = roomPlayerList.stream()
                    .filter(x -> x.getPlayer().getId().equals(connection.getClient().getActivePlayer().getId())).findFirst();

            final short playerPosition = roomPlayer.get().getPosition();
            boolean isMaster = roomPlayer.isPresent() && roomPlayer.get().isMaster();
            roomPlayerList.removeIf(rp -> rp.getPlayer().getId().equals(connection.getClient().getActivePlayer().getId()));
            if (isMaster) {
                roomPlayerList.stream()
                        .filter(rp -> !rp.isMaster())
                        .findFirst()
                        .ifPresent(rp -> rp.setMaster(true));
            }
            connection.getClient().getActiveRoom().setRoomPlayerList(roomPlayerList);
            connection.getClient().getActiveRoom().getPositions().set(playerPosition, RoomPositionState.Free);

            this.gameHandler.getRoomList().removeIf(r -> r.getRoomPlayerList().isEmpty());

            S2CRoomPlayerInformationPacket roomPlayerInformationPacket = new S2CRoomPlayerInformationPacket(roomPlayerList);
            this.gameHandler.getClientsInRoom(connection.getClient().getActiveRoom().getRoomId()).forEach(c -> {
                if (!c.getActivePlayer().getId().equals(connection.getClient().getActivePlayer().getId()))
                    c.getConnection().sendTCP(roomPlayerInformationPacket);
            });

            S2CRoomListAnswerPacket roomListAnswerPacket = new S2CRoomListAnswerPacket(this.gameHandler.getRoomList());
            this.gameHandler.getClientsInLobby().forEach(c -> c.getConnection().sendTCP(roomListAnswerPacket));

            S2CRoomPositionChangeAnswerPacket roomPositionChangeAnswerPacket = new S2CRoomPositionChangeAnswerPacket((char) 0, playerPosition, (short) -1);
            this.gameHandler.getClientsInRoom(connection.getClient().getActiveRoom().getRoomId()).forEach(c -> c.getConnection().sendTCP(roomPositionChangeAnswerPacket));

            connection.getClient().setActiveRoom(null);
        }
    }

    private void updateRoomForAllPlayersInMultiplayer(Connection connection, Room room) {
        S2CRoomInformationPacket roomInformationPacket = new S2CRoomInformationPacket(room);
        connection.sendTCP(roomInformationPacket);
        this.gameHandler.getClientsInRoom(room.getRoomId()).forEach(c -> c.getConnection().sendTCP(roomInformationPacket));
        this.refreshLobbyRoomListForAllClients(connection, getRoomMode(room));
    }

    private int getRoomMode(Room room) {
        if (room.getAllowBattlemon() == 2) {
            return GameMode.BATTLEMON;
        }

        return room.getMode();
    }
}
