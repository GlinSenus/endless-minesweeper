package com.odoncharles.endlessminesweep;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

public class GameView extends SurfaceView implements Runnable {
    
    private Thread gameThread;
    private SurfaceHolder holder;
    
    
    private volatile boolean isRunning;
    private boolean initialized = false;
    
    private Paint paint;
    private Typeface font_regular, font_bold, font_italic;
    // Game state variables
    
    private int sw;
    private int sh;
    
    private int mfCols = 16;
    private int mfRows = 27;
    private int xMargin = 30;
    //private int yMargin = 120;
    private Rect minezone;
    
    private int[][] mapData = new int[mfRows][mfCols];
    
    private int tileSize = 64;
    private int uiSubUnit = 64;
    private String[] spriteNames = {"0", "1", "2","3","4","5","6","7","8","flag","fog","grass1","grass2","grass3","grass4","mine","crater"};
    private String[] uiNames = {"untoggled_flag", "toggled_flag", "save_button", "disabled_save_button", "points_bg", "timer_bg", "hint_btn", "restart_btn"};
    private HashMap<String, Bitmap> tileSprites, uiSprites;
    
    private Tile[][] tileMap; // The object-based map

    private int safeTiles;
    private int openSafeTiles = 0;
    private int mines = 30;
    
    private int pStepLen;
    private volatile boolean flag_mode = false;
    
    
    private long timer;
    private volatile long timeSeconds = 0;
    private volatile long score = 0;
    
    private boolean gameover, completed;
    
    private Rect flag_button;
    private RectF progressBarFrame, progressBar;
    
    
    public GameView(Context context) {
        super(context);
        holder = getHolder();
        paint = new Paint();
        tileSprites = new HashMap<String, Bitmap>();
        uiSprites = new HashMap<String, Bitmap>();
        
        minezone = new Rect();
        flag_button = new Rect();
        progressBar = new RectF();
        progressBarFrame = new RectF();
        
        gameover = false;
        completed = false;
    }
    
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // These are the actual pixels available for drawing
        this.sw = w;
        this.sh = h;
        
        tileSize = (sw-(2 * xMargin))/mfCols;
        uiSubUnit = (sw-(2 * xMargin))/10;
        loadAssets();
        initGame();
    }
    
    public void initGame(){
        initialized = false;

        int sbx = sw-xMargin-(uiSubUnit * 3 - 30);
         
        int fbx = (sw - uiSprites.get("toggled_flag").getWidth()) / 2;
        int fby = sh - (2 * xMargin) - uiSubUnit + 10;
        flag_button.set(
            fbx,
            fby,
            fbx + uiSprites.get("toggled_flag").getWidth(),
            fby + uiSubUnit
        );
        
        genMineField(mines);
        loadMineField();
        
        safeTiles = 0;
        for(int y = 0; y < mfRows; y++){
            for(int x = 0; x < mfCols; x++){
                if(mapData[y][x] == 1){
                    safeTiles++;
                }
            }
        }
        
        int pbfy = sh - ((sh-(mfRows * tileSize))/2) - 15;
        progressBarFrame.set(
            xMargin,
            pbfy,
            sw-xMargin,
            pbfy + 30
        );
        progressBar.set(
            xMargin,
            pbfy,
            xMargin,
            pbfy + 30
        );
        
        pStepLen = (tileSize * mfCols)/safeTiles;
        
        timer = System.currentTimeMillis();
        initialized = true;
    }
    
    private void loadAssets(){
        for(String s: spriteNames){
            tileSprites.put(s, loadAndResizeBitmap("tiles/"+s+".png", tileSize, tileSize));
        }
        for(String s: uiNames){
            if(s.contains("_flag") || s.contains("_btn")){
                uiSprites.put(s, loadAndResizeBitmap("ui/"+s+".png", uiSubUnit * 3, uiSubUnit));
            }
            else if(s.contains("_bg")){
                uiSprites.put(s, loadAndResizeBitmap("ui/"+s+".png", uiSubUnit * 4, uiSubUnit - 20));
            }
            else if(s.contains("save_button")){
                uiSprites.put(s, loadAndResizeBitmap("ui/"+s+".png", uiSubUnit * 3 - 30, uiSubUnit - 10));
            }
        }   
        try {
            font_regular = Typeface.createFromAsset(
                getContext().getAssets(), 
                "fonts/CreatoDisplay-Regular.otf"
            );
            font_italic = Typeface.createFromAsset(
                getContext().getAssets(), 
                "fonts/CreatoDisplay-RegularItalic.otf"
            );
            font_bold = Typeface.createFromAsset(
                getContext().getAssets(), 
                "fonts/CreatoDisplay-Bold.otf"
            );
        } catch (Exception e) {
            e.printStackTrace();
            font_regular = Typeface.DEFAULT;
            font_italic = Typeface.DEFAULT;
            font_bold = Typeface.DEFAULT; // Fallback if file is missing
        }
    }
    
    private void genMineField(int numMines) {
        int margin = 1; 
        for (int y = 0; y < mfRows; y++) {
            for (int x = 0; x < mfCols; x++) {
                if (y < margin || y >= mfRows - margin || x < margin || x >= mfCols - margin) {
                    mapData[y][x] = 0; // Solid outer wall
                } else {
                    // 15% Grass (0), 85% Safe (1)
                    mapData[y][x] = (Math.random() < 0.15) ? 0 : 1;
                }
            }
        }
    
        smoothMap(3); 
        
        if (Math.random() < 0.7) {
            int islandsToAdd = (int) (Math.random() * 4) + 1;
            for (int i = 0; i < islandsToAdd; i++) {
                placeIsland();
            }
        }
        
        ensureConnectivity();
    
        int minesPlaced = 0;
        while (minesPlaced < numMines) {
            int rx = (int) (Math.random() * mfCols);
            int ry = (int) (Math.random() * mfRows);
    
            if (mapData[ry][rx] == 1) { 
                mapData[ry][rx] = 2;
                minesPlaced++;
            }
        }
    }
 
    private void placeIsland() {
        int cx = (int) (Math.random() * (mfCols - 6)) + 3;
        int cy = (int) (Math.random() * (mfRows - 6)) + 3;
        
        int blobs = (int) (Math.random() * 3) + 2;
    
        for (int b = 0; b < blobs; b++) {
            int offsetX = (int) (Math.random() * 3) - 1; // -1 to 1
            int offsetY = (int) (Math.random() * 3) - 1;
            
            float radius = (float) (Math.random() * 1.5) + 1.0f;
            
            for (int y = -2; y <= 2; y++) {
                for (int x = -2; x <= 2; x++) {
                    int tx = cx + offsetX + x;
                    int ty = cy + offsetY + y;
    
                    if (tx >= 0 && tx < mfCols && ty >= 0 && ty < mfRows) {
                        double dist = Math.sqrt(x * x + y * y);
                        if (dist <= radius + (Math.random() * 0.5)) {
                            mapData[ty][tx] = 0;
                        }
                    }
                }
            }
        }
    }
    
    private void smoothMap(int iterations) {
        for (int i = 0; i < iterations; i++) {
            int[][] tempMap = new int[mfRows][mfCols];
            for (int y = 1; y < mfRows - 1; y++) {
                for (int x = 1; x < mfCols - 1; x++) {
                    int neighborGrass = 0;
                    for (int ni = -1; ni <= 1; ni++) {
                        for (int nj = -1; nj <= 1; nj++) {
                            if (mapData[y + ni][x + nj] == 0) neighborGrass++;
                        }
                    }
                    if (neighborGrass > 4) tempMap[y][x] = 0;
                    else tempMap[y][x] = 1;
                }
            }
            for (int y = 1; y < mfRows - 1; y++) {
                System.arraycopy(tempMap[y], 1, mapData[y], 1, mfCols - 2);
            }
        }
    }


    private void ensureConnectivity() {
        for (int y = 1; y < mfRows - 1; y++) {
            for (int x = 1; x < mfCols - 1; x++) {
                if (mapData[y][x] != 0) { if (mapData[y-1][x] == 0 && mapData[y+1][x] == 0 &&
                        mapData[y][x-1] == 0 && mapData[y][x+1] == 0) {
                        mapData[y][x] = 0;
                    }
                }
            }
        }
    }
    
    public void loadMineField() {
        tileMap = new Tile[mfRows][mfCols];
        minezone.set(xMargin, (sh - 20 - (mfRows * tileSize))/2, sw-xMargin, sh - ((sh-(mfRows * tileSize))/2) - 40);
        for (int y = 0; y < mfRows; y++) {
            for (int x = 0; x < mfCols; x++) {
                int px = (x * tileSize) + xMargin;
                int py = (y * tileSize) + (sh - 40 - (mfRows * tileSize))/2;
                int type = mapData[y][x]; 
                int rand = (int)(Math.random() * (4 - 1 + 1)) + 1;
                String initialSprite = (type == 0) ? "grass"+rand : "fog";
                
                tileMap[y][x] = new Tile(px, py, x, y, tileSize, type, initialSprite);
            }
        }
        for (int y = 0; y < mfRows; y++) {
            for (int x = 0; x < mfCols; x++) {
                if (tileMap[y][x].typeIndex == 1) {
                    int mineCount = 0;
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            int ny = y + dy;
                            int nx = x + dx;
    
                            // Check boundaries to avoid ArrayIndexOutOfBounds
                            if (ny >= 0 && ny < mfRows && nx >= 0 && nx < mfCols) {
                                if (tileMap[ny][nx].typeIndex == 2) {
                                    mineCount++;
                                }
                            }
                        }
                    }
                    tileMap[y][x].minesAround = mineCount;
                    tileMap[y][x].updateSprite();
                }
            }
        }
    }

    @Override
    public void run() {
        // Target 60 FPS
        long targetTime = 1000 / 60; 

        while (isRunning) {
            long startTime = System.currentTimeMillis();
            
            if(initialized){
                update();
                draw();
            }
           
            long timeTaken = System.currentTimeMillis() - startTime;
            long sleepTime = targetTime - timeTaken;

            if (sleepTime > 0) {
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    
    public Bitmap loadAndResizeBitmap(String fileName, int targetW, int targetH) {
        AssetManager assetManager = this.getResources().getAssets();
        Bitmap bitmap = null;
        
        try {
            InputStream is = assetManager.open("img/" + fileName);
            
            Bitmap original = BitmapFactory.decodeStream(is);
            is.close();
            
            bitmap = Bitmap.createScaledBitmap(original, targetW, targetH, true);
            
            if (original != bitmap) {
                original.recycle(); 
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        return bitmap;
    }

    private void update() {
        if(completed || gameover){
            gameRestart();
        }
        if(System.currentTimeMillis() - timer >= 1000){
            timer = System.currentTimeMillis();
            timeSeconds++;
        }
        if(safeTiles > 0 && safeTiles == openSafeTiles){
            progressBar.right = progressBarFrame.right;
            completed = true;
        }
    }

    private void draw() {
        if (holder.getSurface().isValid()) {
            Canvas canvas = holder.lockCanvas();
            
            canvas.drawColor(Color.argb(255, 205, 161, 122));
            paint.setColor(Color.DKGRAY);
            
            if (tileMap != null) {
                for (int y = 0; y < mfRows; y++) {
                    for (int x = 0; x < mfCols; x++) {
                        Tile t = tileMap[y][x];
                        Bitmap b = tileSprites.get(t.spriteName);
                        
                        if (b != null) {
                            canvas.drawBitmap(b, t.x, t.y, paint);
                        }
                    }
                }
            }
            
          
            if(!flag_mode){
                canvas.drawBitmap(uiSprites.get("toggled_flag"), null, flag_button, paint);
            }
            else{
                canvas.drawBitmap(uiSprites.get("untoggled_flag"), null, flag_button, paint);
            }
            
            canvas.drawBitmap(
                uiSprites.get("points_bg"),
                xMargin,
                xMargin,
                paint
            );
            
            canvas.drawBitmap(
                uiSprites.get("timer_bg"),
                xMargin,
                xMargin + uiSubUnit - 10,
                paint
            );
            
            paint.setColor(Color.argb(255, 250, 199, 90));
            paint.setTextSize(24);
            paint.setAntiAlias(true);
            paint.setTypeface(font_regular);
            
            paint.setStyle(Paint.Style.FILL); 
            canvas.drawRoundRect(progressBar, 10, 10, paint);
            
            paint.setStyle(Paint.Style.STROKE); 
            paint.setStrokeWidth(3);
            paint.setFakeBoldText(true);
            canvas.drawRoundRect(progressBarFrame, 10, 10, paint);
            
            paint.setColor(Color.BLACK);
            paint.setStyle(Paint.Style.FILL); 
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText((safeTiles-openSafeTiles) + " LEFT", sw/2, progressBar.centerY() - ((paint.descent() + paint.ascent()) / 2f), paint);
            
            canvas.drawText(score+" POINTS",
                xMargin + (uiSprites.get("points_bg").getWidth()/2) + 20,
                xMargin + (uiSubUnit/2) - 10 - ((paint.ascent() + paint.descent())/2f),
                paint
            );
            canvas.drawText(String.format("%02d",(timeSeconds / 3600)) + " : "
                + String.format("%02d", ((timeSeconds % 3600) / 60)) + " : "
                + String.format("%02d", (timeSeconds % 60)),
                xMargin + (uiSprites.get("timer_bg").getWidth()/2) + 20,
                xMargin + uiSubUnit + (uiSubUnit/2) - 20 - ((paint.ascent() + paint.descent())/2f),
                paint
            );
                    
            paint.setColor(Color.DKGRAY); 
            
            holder.unlockCanvasAndPost(canvas);
        }
    }
    

    public void resume() {
        isRunning = true;
        gameThread = new Thread(this);
        gameThread.start();
    }

    public void pause() {
        isRunning = false;
        try {
            gameThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    
    private void gameOver(Tile clickedMine) {
        
        java.util.List<Tile> hiddenMines = new java.util.ArrayList<>();
        for (int y = 0; y < mfRows; y++) {
            for (int x = 0; x < mfCols; x++) {
                Tile t = tileMap[y][x];
                if (t.typeIndex == 2 && !t.isRevealed()) {
                    hiddenMines.add(t);
                }
            }
        }
        java.util.Collections.sort(hiddenMines, new java.util.Comparator<Tile>() {
            @Override
            public int compare(Tile a, Tile b) {
                // Calculate squared distance (no need for sqrt as it's slower and doesn't change order)
                double distA = Math.pow(a.gridX - clickedMine.gridX, 2) + Math.pow(a.gridY - clickedMine.gridY, 2);
                double distB = Math.pow(b.gridX - clickedMine.gridX, 2) + Math.pow(b.gridY - clickedMine.gridY, 2);
                
                if (distA < distB) return -1;
                if (distA > distB) return 1;
                return 0;
            }
        });

        new Thread(new Runnable() {
            @Override
            public void run() {
                for (Tile t : hiddenMines) {
                    try {
                        Thread.sleep(100); 
                        t.setRevealed(true);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
        
                gameover = true;
            }
        }).start();

    }

    
    private void gameRestart(){
        if(gameover){
            timeSeconds = 0;
            score = 0;
        }
        completed = false;
        gameover = false;
        openSafeTiles = 0;
        initGame();
    }
    
    
    
    @Override
    public boolean onTouchEvent(android.view.MotionEvent event) {
        int touchX = (int) event.getX();
        int touchY = (int) event.getY();
    
        switch (event.getAction()) {
            case android.view.MotionEvent.ACTION_DOWN:
                if (flag_button.contains(touchX, touchY)) {
                    flag_mode = !flag_mode;
                    return true; 
                }
           
                if (minezone.contains(touchX, touchY)) {
                    for (int y = 0; y < mfRows; y++) {
                        for (int x = 0; x < mfCols; x++) {
                            Tile t = tileMap[y][x];
                            if (t.rect.contains(touchX, touchY)) {
                                handleTileClick(t);
                                return true; 
                            }
                        }
                    }
                }
                break;
    
            case android.view.MotionEvent.ACTION_UP:
               
                break;
        }
        
        return true;
    }

    
    private void handleTileClick(Tile t) {
        if(!t.isRevealed() && flag_mode){
            t.toggleFlag();
            return;
        }
        if (t.typeIndex == 0) {
            return; 
        }
    
        if (!t.isRevealed() && !t.isFlagged()) {
            t.setRevealed(true);
            if(t.typeIndex != 2){
                openSafeTiles++;
                addLevelProgress();
                score += 1 + (t.minesAround * 2);
            }
            if (t.typeIndex == 2) {
                gameOver(t);
            }
            else if (t.minesAround == 0) {
                revealNeighbors(t.gridX, t.gridY);
            }
        }
    }
    
    private void revealNeighbors(int x, int y) {
            // 1. Loop through all 8 neighbors
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    
                    // Skip the tile itself (0,0)
                    if (dx == 0 && dy == 0) continue;
        
                    int nx = x + dx;
                    int ny = y + dy;
        
                    // 2. Bounds check: Ensure neighbor is inside the grid
                    if (nx >= 0 && nx < mfCols && ny >= 0 && ny < mfRows) {
                        Tile neighbor = tileMap[ny][nx];
        
                        // 3. Logic Check: Only reveal if it's Safe AND not already revealed/flagged
                        // We skip Grass (0) and Mines (2)
                        if (neighbor.typeIndex == 1 && !neighbor.isRevealed() && !neighbor.isFlagged()) {
                            
                            neighbor.setRevealed(true);
                            openSafeTiles++;
                            addLevelProgress();
                            score += 1 + (neighbor.minesAround * 2);
                            // 4. Recursive Call: If this neighbor is also a '0', expand from here
                            if (neighbor.minesAround == 0) {
                                revealNeighbors(nx, ny);
                            }
                        }
                    }
                }
            }
        }
        
    private void addLevelProgress(){
        progressBar.right += pStepLen;
    }
    
    private void explodeTile(Tile t){
        //use the coordinate of the tile to play a replayable explosion animation.
    }
}
