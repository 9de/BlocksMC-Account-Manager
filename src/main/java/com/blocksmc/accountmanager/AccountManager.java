package com.blocksmc.accountmanager;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.client.C01PacketChatMessage;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Optional;

@Mod(modid = "bmcaccountmanager", version = "1.0", clientSideOnly = true)
public class AccountManager {
    private static Configuration config;
    private static final List<Account> accounts = new ArrayList<>();
    private static final ConcurrentHashMap<String, DynamicTexture> iconCache = new ConcurrentHashMap<>();
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static boolean autoLoginEnabled = true; // Add auto login toggle
    
    public static class Account {
        private String username;
        private String password;
        
        public Account(String username, String password) {
            this.username = username;
            this.password = password;
        }
        
        public String getUsername() { return username; }
        public String getPassword() { return password; }
        public void setUsername(String username) { this.username = username; }
        public void setPassword(String password) { this.password = password; }
    }

    public static List<Account> getAccounts() {
        return accounts;
    }
    
    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        File configDir = new File("config");
        if (!configDir.exists()) {
            configDir.mkdir();
        }
        config = new Configuration(new File(configDir, "accountmanager.cfg"));
        loadAccounts();
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(this);
    }
    
    @SubscribeEvent
    public void onServerConnect(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        if (mc.getCurrentServerData() == null || !autoLoginEnabled) return;
        
        String serverAddress = mc.getCurrentServerData().serverIP.toLowerCase();
        if (serverAddress.contains("blocksmc.com") || serverAddress.contains("play.blocksmc.com") || serverAddress.contains("ccc.blocksmc.com")) {
            new Thread(() -> {
                try {
                    // Wait for connection to stabilize
                    Thread.sleep(500);
                    String currentUsername = mc.getSession().getUsername();
                    
                    // Search for matching account
                    Optional<Account> matchingAccount = accounts.stream()
                        .filter(account -> account.getUsername().equalsIgnoreCase(currentUsername))
                        .findFirst();
                    
                    if (matchingAccount.isPresent()) {
                        // Account found - attempt login
                        NetHandlerPlayClient netHandler = mc.getNetHandler();
                        if (netHandler != null) {
                            // Send login command
                        	 mc.ingameGUI.getChatGUI().printChatMessage(
                                     new net.minecraft.util.ChatComponentText(
                                         "\u00A78[\u00A7bBMC MANAGER\u00A78] \u00A7aAccount found! Attempting auto-login..."
                                     )
                                );
                            netHandler.addToSendQueue(new C01PacketChatMessage("/login " + matchingAccount.get().getPassword()));
                            

                           
                            
                        }
                    } else {
                        // Account not found - send client-side warning
                        mc.ingameGUI.getChatGUI().printChatMessage(
                            new net.minecraft.util.ChatComponentText(
                                "\u00A78[\u00A7bBMC MANAGER\u00A78] \u00A7cNo saved account found for: \u00A7f" + currentUsername
                            )
                        );
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }, "AccountLogin-Thread").start();
        }
    }
    @SubscribeEvent
    public void onGuiInit(GuiScreenEvent.InitGuiEvent.Post event) {
        if (event.gui instanceof GuiMainMenu) {
            event.buttonList.stream()
                .filter(button -> button.id == 2)
                .findFirst()
                .ifPresent(button -> {
                    int newX = button.xPosition + button.width + 4;
                    event.buttonList.add(new AccountButton(30000, newX, button.yPosition));
                });
        }
    }

 // First, let's fix the BlocksMC icon loading method
    private static DynamicTexture loadBlocksMCIcon() {
        try {
            return iconCache.computeIfAbsent("blocksmc", name -> {
                try {
                    // Using a more reliable icon URL and fallback
                    URL url = new URL("https://dl.labymod.net/img/server/blocksmc/icon@2x.png");
                    BufferedImage image = ImageIO.read(url);
                    
                    
                    // Create a properly sized icon if loaded
                    if (image != null) {
                        BufferedImage resized = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
                        java.awt.Graphics2D g = resized.createGraphics();
                        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, 
                                         java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                        g.drawImage(image, 0, 0, 256, 256, null);
                        g.dispose();
                        return new DynamicTexture(resized);
                    }
                    return null;
                } catch (IOException e) {
                    e.printStackTrace();
                    return null;
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static boolean isAutoLoginEnabled() {
        return autoLoginEnabled;
    }

    public static void setAutoLoginEnabled(boolean enabled) {
        autoLoginEnabled = enabled;
        config.get(Configuration.CATEGORY_GENERAL, "autoLoginEnabled", true).set(enabled);
        config.save();
    }

    public static void loadAccounts() {
        accounts.clear();
        config.load();
        autoLoginEnabled = config.getBoolean("autoLoginEnabled", Configuration.CATEGORY_GENERAL, true, "Enable auto login");
        String[] accountList = config.getStringList("accounts", Configuration.CATEGORY_GENERAL, 
            new String[]{}, "List of saved accounts");
        
        for (String acc : accountList) {
            String[] parts = acc.split(":");
            if (parts.length == 2) {
                accounts.add(new Account(parts[0], parts[1]));
            }
        }
        
        if (config.hasChanged()) {
            config.save();
        }
    }
    
    public static void saveAccounts() {
        String[] accountList = new String[accounts.size()];
        for (int i = 0; i < accounts.size(); i++) {
            Account acc = accounts.get(i);
            accountList[i] = acc.getUsername() + ":" + acc.getPassword();
        }
        config.getCategory(Configuration.CATEGORY_GENERAL)
            .get("accounts").set(accountList);
        config.save();
    }


    public static class AccountButton extends GuiButton {
        public AccountButton(int buttonId, int x, int y) {
            super(buttonId, x, y, 100, 20, "BMC MANAGER");
        }
        
        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY) {
            if (this.visible) {
                this.hovered = mouseX >= this.xPosition && mouseY >= this.yPosition && 
                              mouseX < this.xPosition + this.width && mouseY < this.yPosition + this.height;
                
                int startColor = 0xFF1E90FF;
                int endColor = 0xFF4169E1;
                drawGradientRect(this.xPosition, this.yPosition, 
                               this.xPosition + this.width, this.yPosition + this.height,
                               this.hovered ? endColor : startColor,
                               this.hovered ? startColor : endColor);
                
                this.drawCenteredString(mc.fontRendererObj, this.displayString,
                    this.xPosition + this.width / 2,
                    this.yPosition + (this.height - 8) / 2,
                    0xFFFFFF);
            }
        }
        
        @Override
        public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
            if (super.mousePressed(mc, mouseX, mouseY)) {
                mc.displayGuiScreen(new GuiAccountManager());
                return true;
            }
            return false;
        }
    }

    public static class GuiAccountManager extends GuiScreen {
        private final List<GuiButton> accountButtons = new ArrayList<>();
        private GuiButton addButton;
        private GuiButton backButton;
        private GuiButton toggleAutoLoginButton;
        private GuiButton prevPageButton;
        private GuiButton nextPageButton;
        private DynamicTexture blocksMCIcon;
        
        private int currentPage = 0;
        private static final int ACCOUNTS_PER_PAGE = 6;
        
        @Override
        public void initGui() {
            this.buttonList.clear();
            this.accountButtons.clear();
            
            blocksMCIcon = loadBlocksMCIcon();

            // Calculate total pages
            int totalPages = (int) Math.ceil(accounts.size() / (double) ACCOUNTS_PER_PAGE);
            
            // Ensure current page is within bounds
            currentPage = Math.max(0, Math.min(currentPage, totalPages - 1));

            // Add main buttons
            addButton = new GuiButton(0, this.width / 2 - 100, this.height - 76, 
                200, 20, "\u00A7a\u00A7l+ \u00A7r\u00A7fAdd Account");
            toggleAutoLoginButton = new GuiButton(1, this.width / 2 - 100, this.height - 52,
                200, 20, (isAutoLoginEnabled() ? "\u00A7a" : "\u00A7c") + "Auto Login: " + (isAutoLoginEnabled() ? "ON" : "OFF"));
            backButton = new GuiButton(2, this.width / 2 - 100, this.height - 28, 
                200, 20, "\u00A7c\u2190 Back");
            
            // Add pagination buttons
            prevPageButton = new GuiButton(3, this.width / 2 - 100, this.height - 100,
                95, 20, "< Previous");
            nextPageButton = new GuiButton(4, this.width / 2 + 5, this.height - 100,
                95, 20, "Next >");
            
            // Update pagination button states
            prevPageButton.enabled = currentPage > 0;
            nextPageButton.enabled = currentPage < totalPages - 1;
            
            this.buttonList.add(addButton);
            this.buttonList.add(toggleAutoLoginButton);
            this.buttonList.add(backButton);
            this.buttonList.add(prevPageButton);
            this.buttonList.add(nextPageButton);
            
            // Calculate start and end indices for current page
            int startIndex = currentPage * ACCOUNTS_PER_PAGE;
            int endIndex = Math.min(startIndex + ACCOUNTS_PER_PAGE, accounts.size());
            
            // Add account buttons for current page
            int y = 50;
            for (int i = startIndex; i < endIndex; i++) {
                Account account = accounts.get(i);
                GuiButton accButton = new AccountListButton(i + 5, 
                    this.width / 2 - 100, y, 200, 30, account.getUsername());
                this.buttonList.add(accButton);
                this.accountButtons.add(accButton);
                y += 35;
            }
        }
        
        @Override
        protected void actionPerformed(GuiButton button) throws IOException {
            if (button == addButton) {
                mc.displayGuiScreen(new GuiAddAccount(this));
            } else if (button == toggleAutoLoginButton) {
                setAutoLoginEnabled(!isAutoLoginEnabled());
                toggleAutoLoginButton.displayString = (isAutoLoginEnabled() ? "\u00A7a" : "\u00A7c") + "Auto Login: " + (isAutoLoginEnabled() ? "ON" : "OFF");
            } else if (button == backButton) {
                mc.displayGuiScreen(new GuiMainMenu());
            } else if (button == prevPageButton && button.enabled) {
                currentPage--;
                initGui();
            } else if (button == nextPageButton && button.enabled) {
                currentPage++;
                initGui();
            } else if (accountButtons.contains(button)) {
                int index = currentPage * ACCOUNTS_PER_PAGE + accountButtons.indexOf(button);
                mc.displayGuiScreen(new GuiEditAccount(this, accounts.get(index)));
            }
        }
        
        private void drawCredits() {
            String creditText = "\u00A77Created by \u00A7bTvrki \u00A77(Discord: \u00A7b50y\u00A77)";
            int creditWidth = this.fontRendererObj.getStringWidth(creditText);
            this.drawString(this.fontRendererObj, creditText,
                this.width - creditWidth - 4, this.height - 12, 0xFFFFFF);
        }

        
        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            drawRect(0, 0, this.width, this.height, 0xD0000000);
            
            if (blocksMCIcon != null) {
                GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                mc.getTextureManager().bindTexture(
                    mc.getTextureManager().getDynamicTextureLocation("blocksmc_icon", blocksMCIcon));
                drawModalRectWithCustomSizedTexture(
                    10, 10, 0, 0, 32, 32, 32, 32
                );
            }
            
            this.drawCenteredString(this.fontRendererObj, "\u00A7l\u00A76BMC Account Manager", 
                    this.width / 2, 20, 0xFFFFFF);
                
                int totalPages = (int) Math.ceil(accounts.size() / (double) ACCOUNTS_PER_PAGE);
                String accountCount = String.format("\u00A77%d Account%s \u00A78(Page %d/%d)", 
                    accounts.size(), 
                    accounts.size() == 1 ? "" : "s",
                    currentPage + 1,
                    Math.max(1, totalPages));
                this.drawCenteredString(this.fontRendererObj, accountCount, 
                    this.width / 2, 35, 0xAAAAAA);

                // Draw credits
                String creditText = "\u00A77Created by \u00A7bTvrki \u00A77(Discord: \u00A7b50y\u00A77)";
                int creditWidth = this.fontRendererObj.getStringWidth(creditText);
                this.drawString(this.fontRendererObj, creditText,
                    this.width - creditWidth - 4, this.height - 12, 0xFFFFFF);
                
                super.drawScreen(mouseX, mouseY, partialTicks);
            }

    public static class AccountListButton extends GuiButton {
        private static final int HEAD_SIZE = 16;  // Reduced from 32 to 28 for better fit
        private static final int SKIN_PADDING = 4;  // Increased from 5 to 8 for better spacing
        private static final int USERNAME_PADDING = 24; // Adjusted for new head size
        private static final int ICON_SPACING = 15;
        
        private static final String EDIT_ICON = "\u270E";
        private static final String DELETE_ICON = "\u2716";
        
        private static final int HOVER_BG_COLOR = 0x40FFFFFF;
        private static final int DEFAULT_BG_COLOR = 0x60000000;
        private static final int BORDER_COLOR = 0x40FFFFFF;
        private static final int HOVER_TEXT_COLOR = 0xFFFFFF;
        private static final int DEFAULT_TEXT_COLOR = 0xE0E0E0;
        private static final int HOVER_ICON_COLOR = 0xFFFFFF;
        private static final int DEFAULT_ICON_COLOR = 0xA0A0A0;
        private static final int DEFAULT_SKIN_COLOR = 0x80808080;

        private final DynamicTextureWrapper skinTexture;

        public AccountListButton(int id, int x, int y, int width, int height, String username) {
            super(id, x, y, width, height, username);
            this.skinTexture = new DynamicTextureWrapper(username);
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY) {
            if (!visible) return;
            
            updateHoverState(mouseX, mouseY);
            drawBackground();
            drawBorder();
            drawHead(mc);
            drawUsername(mc);
            drawIcons(mc);
        }

        private void updateHoverState(int mouseX, int mouseY) {
            this.hovered = isMouseOver(mouseX, mouseY);
        }

        private boolean isMouseOver(int mouseX, int mouseY) {
            return mouseX >= xPosition && mouseY >= yPosition &&
                   mouseX < xPosition + width && mouseY < yPosition + height;
        }

        private void drawBackground() {
            drawRect(xPosition, yPosition, 
                    xPosition + width, yPosition + height,
                    hovered ? HOVER_BG_COLOR : DEFAULT_BG_COLOR);
        }

        private void drawBorder() {
            drawHorizontalLine(xPosition, xPosition + width - 1, yPosition, BORDER_COLOR);
            drawHorizontalLine(xPosition, xPosition + width - 1, yPosition + height - 1, BORDER_COLOR);
            drawVerticalLine(xPosition, yPosition, yPosition + height - 1, BORDER_COLOR);
            drawVerticalLine(xPosition + width - 1, yPosition, yPosition + height - 1, BORDER_COLOR);
        }

        private void drawHead(Minecraft mc) {
            if (skinTexture.bind(mc)) {
                GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                
                // Draw the head with smaller size
                drawModalRectWithCustomSizedTexture(
                    xPosition + SKIN_PADDING,
                    yPosition + (height - HEAD_SIZE) / 2,  // Center vertically
                    0, 0,
                    HEAD_SIZE, HEAD_SIZE,  // Destination size (now 16x16)
                    16, 16  // Source size (changed to match HEAD_SIZE)
                );
            } else {
                // Fallback with adjusted size
                drawRect(
                    xPosition + SKIN_PADDING,
                    yPosition + (height - HEAD_SIZE) / 2,
                    xPosition + SKIN_PADDING + HEAD_SIZE,
                    yPosition + (height - HEAD_SIZE) / 2 + HEAD_SIZE,
                    DEFAULT_SKIN_COLOR
                );
            }
        }

        private void drawUsername(Minecraft mc) {
            drawString(
                mc.fontRendererObj,
                displayString,
                xPosition + USERNAME_PADDING,
                yPosition + (height - 8) / 2,
                hovered ? HOVER_TEXT_COLOR : DEFAULT_TEXT_COLOR
            );
        }

        private void drawIcons(Minecraft mc) {
            int iconColor = hovered ? HOVER_ICON_COLOR : DEFAULT_ICON_COLOR;
            int baseX = xPosition + width;

            drawString(
                mc.fontRendererObj,
                EDIT_ICON,
                baseX - 2 * ICON_SPACING,
                yPosition + (height - 8) / 2,
                iconColor
            );

            drawString(
                mc.fontRendererObj,
                DELETE_ICON,
                baseX - ICON_SPACING,
                yPosition + (height - 8) / 2,
                iconColor
            );
        }

        private static class DynamicTextureWrapper {
            private DynamicTexture texture;
            private final String username;
            private boolean attempted;

            DynamicTextureWrapper(String username) {
                this.username = username;
            }

            boolean bind(Minecraft mc) {
                if (texture == null && !attempted) {
                    attempted = true;
                    texture = loadSkinhead(username);
                }

                if (texture != null) {
                    mc.getTextureManager().bindTexture(
                        mc.getTextureManager().getDynamicTextureLocation("skin_" + username, texture)
                    );
                    return true;
                }
                return false;
            }
        }

        private static final Map<String, DynamicTexture> skinCache = new ConcurrentHashMap<>();

        private static DynamicTexture loadSkinhead(String username) {
            try {
                return skinCache.computeIfAbsent(username, name -> {
                    try {
                        // Request 16x16 image from Minotar
                        URL url = new URL(String.format("https://minotar.net/helm/%s/16.png", name));
                        BufferedImage image = ImageIO.read(url);

                        if (image != null) {
                            return new DynamicTexture(image);
                        }
                        return null;
                    } catch (IOException e) {
                        e.printStackTrace();
                        return null;
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
}

    public static class GuiAddAccount extends GuiScreen {
        private final GuiScreen parentScreen;
        private GuiTextField usernameField;
        private GuiTextField passwordField;
        private GuiButton addButton;
        private GuiButton cancelButton;
        
        public GuiAddAccount(GuiScreen parentScreen) {
            this.parentScreen = parentScreen;
        }
        
        @Override
        public void initGui() {
            this.buttonList.clear();
            
            usernameField = new GuiTextField(0, this.fontRendererObj, 
                this.width / 2 - 100, this.height / 2 - 50, 200, 20);
            passwordField = new GuiTextField(1, this.fontRendererObj, 
                this.width / 2 - 100, this.height / 2 - 10, 200, 20);
            
            addButton = new GuiButton(0, this.width / 2 - 100, this.height / 2 + 30, 
                95, 20, "\u00A7a\u00A7lAdd");
            cancelButton = new GuiButton(1, this.width / 2 + 5, this.height / 2 + 30, 
                95, 20, "\u00A7cCancel");
            
            this.buttonList.add(addButton);
            this.buttonList.add(cancelButton);
            
            addButton.enabled = false;
        }

        @Override
        protected void actionPerformed(GuiButton button) throws IOException {
            if (button == addButton && !usernameField.getText().isEmpty()) {
                String username = usernameField.getText().trim();
                String password = passwordField.getText();
                
                boolean accountExists = accounts.stream()
                    .anyMatch(acc -> acc.getUsername().equalsIgnoreCase(username));
                
                if (!accountExists) {
                    accounts.add(new Account(username, password));
                    saveAccounts();
                }
                
                mc.displayGuiScreen(parentScreen);
            } else if (button == cancelButton) {
                mc.displayGuiScreen(parentScreen);
            }
        }

        @Override
        protected void keyTyped(char typedChar, int keyCode) throws IOException {
            usernameField.textboxKeyTyped(typedChar, keyCode);
            passwordField.textboxKeyTyped(typedChar, keyCode);
            
            if (keyCode == 28 || keyCode == 156) { // Enter key
                if (!usernameField.getText().isEmpty()) {
                    actionPerformed(addButton);
                }
            } else if (keyCode == 1) { // Escape key
                actionPerformed(cancelButton);
            }
            
            addButton.enabled = !usernameField.getText().isEmpty();
            super.keyTyped(typedChar, keyCode);
        }

        @Override
        protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
            usernameField.mouseClicked(mouseX, mouseY, mouseButton);
            passwordField.mouseClicked(mouseX, mouseY, mouseButton);
            super.mouseClicked(mouseX, mouseY, mouseButton);
        }

        @Override
        public void updateScreen() {
            usernameField.updateCursorCounter();
            passwordField.updateCursorCounter();
        }
        
        private void drawCredits() {
            String creditText = "\u00A77Created by \u00A7bTvrki \u00A77(Discord: \u00A7b50y\u00A77)";
            int creditWidth = this.fontRendererObj.getStringWidth(creditText);
            this.drawString(this.fontRendererObj, creditText,
                this.width - creditWidth - 4, this.height - 12, 0xFFFFFF);
        }

        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            drawRect(0, 0, this.width, this.height, 0xD0000000);
            
            this.drawString(this.fontRendererObj, "\u00A7fUsername:", 
                this.width / 2 - 100, this.height / 2 - 65, 0xFFFFFF);
            this.drawString(this.fontRendererObj, "\u00A7fPassword:", 
                this.width / 2 - 100, this.height / 2 - 25, 0xFFFFFF);
            
            usernameField.drawTextBox();
            passwordField.drawTextBox();
            super.drawScreen(mouseX, mouseY, partialTicks);
            // Draw credits after everything else
            drawCredits();
        }
    }

    public static class GuiEditAccount extends GuiScreen {
        private final GuiScreen parentScreen;
        private final Account account;
        private GuiTextField usernameField;
        private GuiTextField passwordField;
        private GuiButton saveButton;
        private GuiButton deleteButton;
        private GuiButton cancelButton;
        
        public GuiEditAccount(GuiScreen parentScreen, Account account) {
            this.parentScreen = parentScreen;
            this.account = account;
        }
        
        @Override
        public void initGui() {
            this.buttonList.clear();
            
            usernameField = new GuiTextField(0, this.fontRendererObj, 
                this.width / 2 - 100, this.height / 2 - 50, 200, 20);
            passwordField = new GuiTextField(1, this.fontRendererObj, 
                this.width / 2 - 100, this.height / 2 - 10, 200, 20);
            
            usernameField.setText(account.getUsername());
            passwordField.setText(account.getPassword());
            
            saveButton = new GuiButton(0, this.width / 2 - 100, this.height / 2 + 30, 
                95, 20, "\u00A7a\u00A7lSave");
            deleteButton = new GuiButton(1, this.width / 2 + 5, this.height / 2 + 30, 
                95, 20, "\u00A7c\u00A7lDelete");
            cancelButton = new GuiButton(2, this.width / 2 - 100, this.height / 2 + 55, 
                200, 20, "\u00A77Cancel");
            
            this.buttonList.add(saveButton);
            this.buttonList.add(deleteButton);
            this.buttonList.add(cancelButton);
            
            saveButton.enabled = !usernameField.getText().isEmpty();
        }
        
        @Override
        protected void actionPerformed(GuiButton button) throws IOException {
            if (button == saveButton && !usernameField.getText().isEmpty()) {
                account.setUsername(usernameField.getText());
                account.setPassword(passwordField.getText());
                saveAccounts();
                mc.displayGuiScreen(parentScreen);
            } else if (button == deleteButton) {
                accounts.remove(account);
                saveAccounts();
                mc.displayGuiScreen(parentScreen);
            } else if (button == cancelButton) {
                mc.displayGuiScreen(parentScreen);
            }
        }
        
        @Override
        protected void keyTyped(char typedChar, int keyCode) throws IOException {
            usernameField.textboxKeyTyped(typedChar, keyCode);
            passwordField.textboxKeyTyped(typedChar, keyCode);
            
            if (keyCode == 28 || keyCode == 156) { // Enter key
                if (!usernameField.getText().isEmpty()) {
                    actionPerformed(saveButton);
                }
            } else if (keyCode == 1) { // Escape key
                actionPerformed(cancelButton);
            }
            
            saveButton.enabled = !usernameField.getText().isEmpty();
            super.keyTyped(typedChar, keyCode);
        }
        
        @Override
        protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
            usernameField.mouseClicked(mouseX, mouseY, mouseButton);
            passwordField.mouseClicked(mouseX, mouseY, mouseButton);
            super.mouseClicked(mouseX, mouseY, mouseButton);
        }
        
        @Override
        public void updateScreen() {
            usernameField.updateCursorCounter();
            passwordField.updateCursorCounter();
        }
        
        private void drawCredits() {
            String creditText = "\u00A77Created by \u00A7bTvrki \u00A77(Discord: \u00A7b50y\u00A77)";
            int creditWidth = this.fontRendererObj.getStringWidth(creditText);
            this.drawString(this.fontRendererObj, creditText,
                this.width - creditWidth - 4, this.height - 12, 0xFFFFFF);
        }
        
        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            drawRect(0, 0, this.width, this.height, 0xD0000000);
            
            this.drawCenteredString(this.fontRendererObj, "\u00A7l\u00A76Edit Account", 
                this.width / 2, this.height / 2 - 100, 0xFFFFFF);
            
            this.drawString(this.fontRendererObj, "\u00A7fUsername:", 
                this.width / 2 - 100, this.height / 2 - 65, 0xFFFFFF);
            this.drawString(this.fontRendererObj, "\u00A7fPassword:", 
                this.width / 2 - 100, this.height / 2 - 25, 0xFFFFFF);
            
            String userPreview = String.format("\u00A77Current: \u00A7f%s", account.getUsername());
            this.drawCenteredString(this.fontRendererObj, userPreview,
                this.width / 2, this.height / 2 + 12, 0xAAAAAA);
            
            usernameField.drawTextBox();
            passwordField.drawTextBox();
            super.drawScreen(mouseX, mouseY, partialTicks);
            drawCredits();

        }
    }
}
}



