/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package com.netcattest.ncatminecraft.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import com.netcattest.ncatminecraft.NcatMinecraft;
import com.netcattest.ncatminecraft.client.gui.controls.*;
import com.netcattest.ncatminecraft.client.gui.loading.FillControl;
import com.netcattest.ncatminecraft.core.ScreenRights;
import com.netcattest.ncatminecraft.entity.ScreenData;
import com.netcattest.ncatminecraft.entity.ScreenBlockEntity;
import com.netcattest.ncatminecraft.item.WDItem;
import com.netcattest.ncatminecraft.net.WDNetworkRegistry;
import com.netcattest.ncatminecraft.net.server_bound.C2SMessageScreenCtrl;
import com.netcattest.ncatminecraft.utilities.*;
import com.netcattest.ncatminecraft.utilities.math.Vector2i;
import com.netcattest.ncatminecraft.utilities.data.BlockSide;
import com.netcattest.ncatminecraft.utilities.data.Rotation;
import com.netcattest.ncatminecraft.utilities.serialization.NameUUIDPair;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

public class GuiScreenConfig extends WDScreen {

    private final ScreenBlockEntity tes;
    private final BlockSide side;
    private NameUUIDPair owner;
    private NameUUIDPair[] friends;
    private int friendRights;
    private int otherRights;
    private Rotation rotation = Rotation.ROT_0;
    private float aspectRatio;

    private boolean waitingAC;
    private int acFailTicks = -1;

    private final ArrayList<NameUUIDPair> acResults = new ArrayList<>();
    private boolean adding;

    @FillControl
    private Label lblOwner;

    @FillControl
    private List lstFriends;

    @FillControl
    private Button btnAdd;

    @FillControl
    private TextField tfFriend;

    @FillControl
    private TextField tfResX;

    @FillControl
    private TextField tfResY;

    @FillControl
    private ControlGroup grpFriends;

    @FillControl
    private ControlGroup grpOthers;

    @FillControl
    private CheckBox boxFSetUrl;

    @FillControl
    private CheckBox boxFClick;

    @FillControl
    private CheckBox boxFFriends;

    @FillControl
    private CheckBox boxFOthers;

    @FillControl
    private CheckBox boxFUpgrades;

    @FillControl
    private CheckBox boxFResolution;

    @FillControl
    private CheckBox boxOSetUrl;

    @FillControl
    private CheckBox boxOClick;

    @FillControl
    private CheckBox boxOUpgrades;

    @FillControl
    private CheckBox boxOResolution;

    @FillControl
    private Button btnSetRes;

    @FillControl
    private UpgradeGroup ugUpgrades;

    @FillControl
    private Button btnChangeRot;

    @FillControl
    private CheckBox cbLockRatio;

    @FillControl
    private CheckBox cbAutoVolume;

    private CheckBox[] friendBoxes;
    private CheckBox[] otherBoxes;

    public GuiScreenConfig(Component component, ScreenBlockEntity tes, BlockSide side, NameUUIDPair[] friends, int fr, int or) {
        super(component);
        this.tes = tes;
        this.side = side;
        this.friends = friends;
        friendRights = fr;
        otherRights = or;
    }

    @Override
    public void init() {
        super.init();
        loadFrom(new ResourceLocation("ncat_minecraft", "gui/screencfg.json"));

        friendBoxes = new CheckBox[] { boxFResolution, boxFUpgrades, boxFOthers, boxFFriends, boxFClick, boxFSetUrl };
        boxFResolution.setUserdata(ScreenRights.MODIFY_SCREEN);
        boxFUpgrades.setUserdata(ScreenRights.MANAGE_UPGRADES);
        boxFOthers.setUserdata(ScreenRights.MANAGE_OTHER_RIGHTS);
        boxFFriends.setUserdata(ScreenRights.MANAGE_FRIEND_LIST);
        boxFClick.setUserdata(ScreenRights.INTERACT);
        boxFSetUrl.setUserdata(ScreenRights.CHANGE_URL);

        otherBoxes = new CheckBox[] { boxOResolution, boxOUpgrades, boxOClick, boxOSetUrl };
        boxOResolution.setUserdata(ScreenRights.MODIFY_SCREEN);
        boxOUpgrades.setUserdata(ScreenRights.MANAGE_UPGRADES);
        boxOClick.setUserdata(ScreenRights.INTERACT);
        boxOSetUrl.setUserdata(ScreenRights.CHANGE_URL);

        ScreenData scr = tes.getScreen(side);
        if(scr != null) {
            owner = scr.owner;
            rotation = scr.rotation;

            tfResX.setText("" + scr.resolution.x);
            tfResY.setText("" + scr.resolution.y);
            aspectRatio = ((float) scr.resolution.x) / ((float) scr.resolution.y);

            ugUpgrades.setUpgrades(scr.upgrades);
            cbAutoVolume.setChecked(scr.autoVolume);
        }

        if(owner == null)
            owner = new NameUUIDPair("???", UUID.randomUUID());

        lblOwner.setLabel(lblOwner.getLabel() + ' ' + owner.name);
        for(NameUUIDPair f : friends)
            lstFriends.addElementRaw(f.name, f);

        lstFriends.updateContent();
        updateRights(friendRights, friendRights, friendBoxes, true);
        updateRights(otherRights, otherRights, otherBoxes, true);
        updateMyRights();
        updateRotationStr();

        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI( NcatMinecraft.INSTANCE.soundScreenCfg, 1.0f, 1.0f));
    }

    private void updateRotationStr() {
        btnChangeRot.setLabel(I18n.get("ncat_minecraft.gui.screencfg.rot" + rotation.getAngleAsInt()));
    }

    private void addFriend(String name) {
        if(!name.isEmpty()) {
            requestAutocomplete(name, true);
            tfFriend.setDisabled(true);
            adding = true;
            waitingAC = true;
        }
    }

    private void clickSetRes() {
        ScreenData scr = tes.getScreen(side);
        if(scr == null)
            return;

        try {
            int x = Integer.parseInt(tfResX.getText());
            int y = Integer.parseInt(tfResY.getText());
            if(x < 1 || y < 1)
                throw new NumberFormatException();

            if(x != scr.resolution.x || y != scr.resolution.y)
                WDNetworkRegistry.INSTANCE.sendToServer(C2SMessageScreenCtrl.resolution(tes, side, new Vector2i(x, y)));
        } catch(NumberFormatException ex) {
            tfResX.setText("" + scr.resolution.x);
            tfResY.setText("" + scr.resolution.y);
        }

        btnSetRes.setDisabled(true);
    }

    @GuiSubscribe
    public void onClick(Button.ClickEvent ev) {
        if(ev.getSource() == btnAdd && !waitingAC)
            addFriend(tfFriend.getText().trim());
        else if(ev.getSource() == btnSetRes)
            clickSetRes();
        else if(ev.getSource() == btnChangeRot) {
            Rotation[] rots = Rotation.values();
            WDNetworkRegistry.INSTANCE.sendToServer(new C2SMessageScreenCtrl(tes, side, rots[(rotation.ordinal() + 1) % rots.length]));
        }
    }

    @GuiSubscribe
    public void onEnterPressed(TextField.EnterPressedEvent ev) {
        if(ev.getSource() == tfFriend && !waitingAC)
            addFriend(ev.getText().trim());
        else if((ev.getSource() == tfResX || ev.getSource() == tfResY) && !btnSetRes.isDisabled())
            clickSetRes();
    }

    @GuiSubscribe
    public void onAutocomplete(TextField.TabPressedEvent ev) {
        if(ev.getSource() == tfFriend && !waitingAC && !ev.getBeginning().isEmpty()) {
            if(acResults.isEmpty()) {
                waitingAC = true;
                requestAutocomplete(ev.getBeginning(), false);
            } else {
                NameUUIDPair pair = acResults.remove(0);
                tfFriend.setText(pair.name);
            }
        } else if(ev.getSource() == tfResX) {
            tfResX.setFocused(false);
            tfResY.focus();
            tfResY.getMcField().setCursorPosition(0);
            tfResY.getMcField().setHighlightPos(tfResY.getText().length());
        }
    }

    @GuiSubscribe
    public void onTextChanged(TextField.TextChangedEvent ev) {
        if(ev.getSource() == tfResX || ev.getSource() == tfResY) {
            for(int i = 0; i < ev.getNewContent().length(); i++) {
                if(!Character.isDigit(ev.getNewContent().charAt(i))) {
                    ev.getSource().setText(ev.getOldContent());
                    return;
                }
            }

            if(cbLockRatio.isChecked()) {
                if(ev.getSource() == tfResX) {
                    try {
                        float val = (float) Integer.parseInt(ev.getNewContent());
                        val /= aspectRatio;
                        tfResY.setText("" + ((int) val));
                    } catch(NumberFormatException ex) {}
                } else {
                    try {
                        float val = (float) Integer.parseInt(ev.getNewContent());
                        val *= aspectRatio;
                        tfResX.setText("" + ((int) val));
                    } catch(NumberFormatException ex) {}
                }
            }

            btnSetRes.setDisabled(false);
        }
    }

    @GuiSubscribe
    public void onRemovePlayer(List.EntryClick ev) {
        if(ev.getSource() == lstFriends)
            WDNetworkRegistry.INSTANCE.sendToServer(new C2SMessageScreenCtrl(tes, side, (NameUUIDPair) ev.getUserdata(), true));
    }

    @GuiSubscribe
    public void onCheckboxChanged(CheckBox.CheckedEvent ev) {
        if(isFriendCheckbox(ev.getSource())) {
            int flag = (Integer) ev.getSource().getUserdata();
            if(ev.isChecked())
                friendRights |= flag;
            else
                friendRights &= ~flag;

            requestSync();
        } else if(isOtherCheckbox(ev.getSource())) {
            int flag = (Integer) ev.getSource().getUserdata();
            if(ev.isChecked())
                otherRights |= flag;
            else
                otherRights &= ~flag;

            requestSync();
        } else if(ev.getSource() == cbLockRatio && ev.isChecked()) {
            try {
                int x = Integer.parseInt(tfResX.getText());
                int y = Integer.parseInt(tfResY.getText());

                aspectRatio = ((float) x) / ((float) y);
            } catch(NumberFormatException ex) {
                cbLockRatio.setChecked(false);
            }
        } else if(ev.getSource() == cbAutoVolume) WDNetworkRegistry.INSTANCE.sendToServer(C2SMessageScreenCtrl.autoVol(tes, side, ev.isChecked()));
    }

    @GuiSubscribe
    public void onRemoveUpgrade(UpgradeGroup.ClickEvent ev) {
        WDNetworkRegistry.INSTANCE.sendToServer(new C2SMessageScreenCtrl(tes, side, ev.getMouseOverStack()));
    }

    public boolean isFriendCheckbox(CheckBox cb) {
        return Arrays.stream(friendBoxes).anyMatch(fb -> cb == fb);
    }

    public boolean isOtherCheckbox(CheckBox cb) {
        return Arrays.stream(otherBoxes).anyMatch(ob -> cb == ob);
    }

    public boolean hasFriend(NameUUIDPair f) {
        return Arrays.stream(friends).anyMatch(f::equals);
    }

    @Override
    public void onAutocompleteResult(NameUUIDPair pairs[]) {
        waitingAC = false;

        if(adding) {
            if(!hasFriend(pairs[0]))
                WDNetworkRegistry.INSTANCE.sendToServer(new C2SMessageScreenCtrl(tes, side, pairs[0], false));

            tfFriend.setDisabled(false);
            tfFriend.clear();
            tfFriend.focus();
            adding = false;
        } else {
            acResults.clear();
            acResults.addAll(Arrays.asList(pairs));

            NameUUIDPair pair = acResults.remove(0);
            tfFriend.setText(pair.name);
        }
    }

    @Override
    public void onAutocompleteFailure() {
        waitingAC = false;
        acResults.clear();
        acFailTicks = 0;
        tfFriend.setTextColor(Control.COLOR_RED);

        if(adding) {
            tfFriend.setDisabled(false);
            adding = false;
        }
    }

    @Override
    public void tick() {
        super.tick();

        if(acFailTicks >= 0) {
            if(++acFailTicks >= 10) {
                acFailTicks = -1;
                tfFriend.setTextColor(TextField.DEFAULT_TEXT_COLOR);
            }
        }
    }

    public void updateFriends(NameUUIDPair[] friends) {
        boolean diff = false;
        if(friends.length != this.friends.length)
            diff = true;
        else {
            for(NameUUIDPair pair : friends) {
                if(!hasFriend(pair)) {
                    diff = true;
                    break;
                }
            }
        }

        if(diff) {
            this.friends = friends;
            lstFriends.clearRaw();
            for(NameUUIDPair pair : friends)
                lstFriends.addElementRaw(pair.name, pair);

            lstFriends.updateContent();
        }
    }

    private int updateRights(int current, int newVal, CheckBox[] boxes, boolean force) {
        if(force || current != newVal) {
            for(CheckBox box : boxes) {
                int flag = (Integer) box.getUserdata();
                box.setChecked((newVal & flag) != 0);
            }

            if(!force) {
                Log.info("Screen check boxes were updated");
                abortSync();
            }
        }

        return newVal;
    }

    public void updateFriendRights(int rights) {
        friendRights = updateRights(friendRights, rights, friendBoxes, false);
    }

    public void updateOtherRights(int rights) {
        otherRights = updateRights(otherRights, rights, otherBoxes, false);
    }

    @Override
    protected void sync() {
        WDNetworkRegistry.INSTANCE.sendToServer(new C2SMessageScreenCtrl(tes, side, friendRights, otherRights));
        Log.info("Sent sync packet");
    }

    public void updateMyRights() {
        NameUUIDPair me = new NameUUIDPair(minecraft.player.getGameProfile());
        int myRights;
        boolean clientIsOwner = false;

        if(me.equals(owner)) {
            myRights = ScreenRights.ALL;
            clientIsOwner = true;
        } else if(hasFriend(me))
            myRights = friendRights;
        else
            myRights = otherRights;

        grpFriends.setDisabled(!clientIsOwner);

        boolean flag = (myRights & ScreenRights.MANAGE_FRIEND_LIST) == 0;
        lstFriends.setDisabled(flag);
        tfFriend.setDisabled(flag);
        btnAdd.setDisabled(flag);

        flag = (myRights & ScreenRights.MANAGE_OTHER_RIGHTS) == 0;
        grpOthers.setDisabled(flag);

        flag = (myRights & ScreenRights.MODIFY_SCREEN) == 0;
        tfResX.setDisabled(flag);
        tfResY.setDisabled(flag);
        btnChangeRot.setDisabled(flag);

        if(flag)
            btnSetRes.setDisabled(true);

        flag = (myRights & ScreenRights.MANAGE_UPGRADES) == 0;
        ugUpgrades.setDisabled(flag);
        cbAutoVolume.setDisabled(flag);
    }

    public void updateResolution(Vector2i res) {
        aspectRatio = ((float) res.x) / ((float) res.y);
        tfResX.setText("" + res.x);
        tfResY.setText("" + res.y);
        btnSetRes.setDisabled(true);
    }

    public void updateRotation(Rotation rot) {
        rotation = rot;
        updateRotationStr();
    }

    public void updateAutoVolume(boolean av) {
        cbAutoVolume.setChecked(av);
    }

    @Override
    public boolean isForBlock(BlockPos bp, BlockSide side) {
        return bp.equals(tes.getBlockPos()) && side == this.side;
    }

    @Nullable
    @Override
    public String getWikiPageName() {
        ItemStack is = ugUpgrades.getMouseOverUpgrade();
        if(is != null) {
            if(is.getItem() instanceof WDItem)
                return ((WDItem) is.getItem()).getWikiName(is);
            else
                return null;
        }

        return "Screen_Configurator";
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            Minecraft.getInstance().setScreen(null);
            return true;
        }
        
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

}
