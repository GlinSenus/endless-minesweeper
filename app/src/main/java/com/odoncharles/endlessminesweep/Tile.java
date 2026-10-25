package com.odoncharles.endlessminesweep;

import android.graphics.Rect;

public class Tile {
    // Positioning
    public int x, y;// Pixel coordinates for drawing
    public int gridX, gridY;
    public Rect rect;
    // Logic & States
    public int typeIndex;    // 0: Grass (Wall), 1: Safe, 2: Mine
    public int minesAround;  // Pre-calculated during map generation
    private boolean isRevealed = false;
    private boolean isFlagged = false;
    
    public String spriteName;

    public Tile(int px, int py, int gx, int gy, int tileSize, int type, String spritename) {
        this.x = px;
        this.y = py;
        this.gridX = gx;
        this.gridY = gy;
        this.rect = new Rect(px, py, px+tileSize, py+tileSize);
        this.typeIndex = type;
        this.spriteName = spritename;
        
        // Initial state
        updateSprite();
    }
    
    public void updateSprite() {
        // 1. If flagged, show flag regardless of other states
        if (isFlagged) {
            spriteName = "flag";
            return;
        }

        // 2. If not revealed, show the "hidden" state
        if (!isRevealed) {
            // Grass acts as a permanent wall/border, Safe/Mines look "hidden"
            spriteName = (typeIndex == 0) ? spriteName : "fog";
            return;
        }

        // 3. If revealed, determine the specific texture
        switch (typeIndex) {
            case 0: // Grass/Wall
                //spriteName = spriteName;
                break;
            case 2: // Mine
                spriteName = "mine";
                break;
            case 1: // Safe Tile
                if (minesAround == 0) {
                    spriteName = "0"; // An empty revealed tile
                } else {
                    // Use the number string "1" through "8"
                    spriteName = String.valueOf(minesAround);
                }
                break;
        }
    }

    // Call these from your GameView touch handler
    public void setRevealed(boolean revealed) {
        this.isRevealed = revealed;
        updateSprite();
    }

    public void toggleFlag() {
        if(typeIndex != 0){
            if (!isRevealed) {
                this.isFlagged = !this.isFlagged;
                updateSprite();
            }
        }
    }

    public boolean isRevealed() { return isRevealed; }
    public boolean isFlagged() { return isFlagged; }
}
