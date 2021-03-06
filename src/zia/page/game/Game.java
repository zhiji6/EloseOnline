package zia.page.game;

import com.google.gson.Gson;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import zia.bean.MapWarper;
import zia.server.Client;
import zia.shape.*;
import zia.util.UserRes;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Game {

    private boolean isEnd;

    private int map[][] = new int[Config.y][Config.x];
    private BaseShape shape;
    private List<Position> tempPositions = new ArrayList<>();
    private final int width = Config.width;
    private final int height = Config.height;
    private int score = 0;
    private int upScore = 10;
    private int time;
    private Canvas gameCanvas;
    private Stage stage;
    private EndListener endListener;
    private String name = UserRes.instance.getUserData().getNickname();

    private Scene gameScene;
    private GraphicsContext gc;
    private double perSize;

    private Thread gameThread;
    private Thread countThread;

    private boolean isSingle;
    private boolean isPause = false;

    public Game(boolean isSingle) {
        this.isSingle = isSingle;
        stage = new Stage();
        stage.setTitle("elose");
        if (!isSingle) {
            stage.setTitle(UserRes.instance.getUserData().getNickname());
            Screen screen = Screen.getPrimary();
            Rectangle2D bounds = screen.getVisualBounds();
            double width = bounds.getMaxX();
            stage.setX(width / 2 - this.width / 2);
        }
        gameCanvas = new Canvas();
        gameCanvas.setWidth(width);
        gameCanvas.setHeight(height);
        Pane pane = new Pane();
        pane.getChildren().add(gameCanvas);
        gameScene = new Scene(pane, width, height);
        stage.setScene(gameScene);
        perSize = width / Config.x;
        gc = gameCanvas.getGraphicsContext2D();
        initShape();
        setKeyBoard();
        isEnd = false;
        stage.show();
    }

    public void setEndListener(EndListener endListener) {
        this.endListener = endListener;
        stage.setOnCloseRequest(event -> {
            isEnd = true;
            endListener.onEnd(score);
        });
    }

    public void begin() {
        score = 0;
        time = 500;
        autoDown();
    }

    public void close() {
        stage.close();
    }

    private void autoDown() {
        countThread = new Thread(() -> {
            while (!isEnd) {
                try {
                    Thread.sleep(1000);
                    time -= 2;
                    score += 2;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        gameThread = new Thread(() -> {
            while (!isEnd) {
                if (!shape.goDown()) {
                    shape.getShape()
                            .forEach(position -> map[position.getY()][position.getX()] = getColor(shape));
                    tempPositions.clear();
                    clearMap();
                    initShape();
                }
                invalidate();
                try {
                    Thread.sleep(time);
                    if (time <= 0) break;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        gameThread.start();
        countThread.start();
    }

    private void initShape() {
        if (create()) {
            invalidate();
        } else {
            gameOver();
        }
    }

    private void gameOver() {
        isEnd = true;
        clearKeyBoard();
        if (!isSingle)
            Client.getInstance().sendData("end");
        if (endListener != null) {
            Platform.runLater(() -> endListener.onEnd(score));
        }
    }

    private boolean create() {
        int k = Math.abs(new Random().nextInt()) % 7;
        try {
            switch (k) {
                case 0:
                    shape = new Line(map);
                    break;
                case 1:
                    shape = new L1(map);
                    break;
                case 2:
                    shape = new L2(map);
                    break;
                case 3:
                    shape = new T(map);
                    break;
                case 4:
                    shape = new Z1(map);
                    break;
                case 5:
                    shape = new Z2(map);
                    break;
                case 6:
                    shape = new TI(map);
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * 刷新ui
     */
    public void invalidate() {
        if (isEnd) return;
        //在数组中消除原来的方块
        for (Position p : tempPositions) {
            map[p.getY()][p.getX()] = Config.EMPTY;
        }
        tempPositions = shape.getShape();
        //将变化后的方块放入数组
        for (Position p : tempPositions) {
            map[p.getY()][p.getX()] = Config.TEMP;
        }

        //刷新ui
        for (int i = 0; i < map.length; i++) {
            for (int j = 0; j < map[0].length; j++) {
                double x = j * perSize;
                double y = i * perSize;
                if (map[i][j] == Config.TEMP) {
                    gc.setFill(Color.valueOf(Config.color[getColor(shape)]));
                } else if (map[i][j] == Config.LINE) {
                    gc.setFill(Color.valueOf(Config.color[1]));
                } else if (map[i][j] == Config.L1) {
                    gc.setFill(Color.valueOf(Config.color[2]));
                } else if (map[i][j] == Config.L2) {
                    gc.setFill(Color.valueOf(Config.color[3]));
                } else if (map[i][j] == Config.T) {
                    gc.setFill(Color.valueOf(Config.color[4]));
                } else if (map[i][j] == Config.TI) {
                    gc.setFill(Color.valueOf(Config.color[5]));
                } else if (map[i][j] == Config.Z1) {
                    gc.setFill(Color.valueOf(Config.color[6]));
                } else if (map[i][j] == Config.Z2) {
                    gc.setFill(Color.valueOf(Config.color[7]));
                }
                gc.fillRect(x + 1, y + 1, perSize - 1, perSize - 1);
                if (map[i][j] == Config.EMPTY) {
                    gc.clearRect(x + 1, y + 1, perSize - 1, perSize - 1);
                }
            }
        }
//        printMap(map);
        gc.fillText("得分：" + score, width - 100, 30);
        gc.fillText("玩家：" + name, 50, 30);
        MapWarper mapWarper = new MapWarper();
        mapWarper.setScore(score);
        mapWarper.setMap(map);
        if (!isSingle)
            Client.getInstance().sendData(new Gson().toJson(mapWarper));
    }

    private void printMap(int map[][]) {
        for (int i = 0; i < map.length; i++) {
            for (int j = 0; j < map[0].length; j++) {
                System.out.print(map[i][j] + " ");
            }
            System.out.println();
        }
        System.out.println("------");
    }

    private void clearKeyBoard() {
        gameScene.setOnKeyPressed(event -> {
            if (event.getCode().getName().equals("Esc") && isSingle){
                if (isPause){
                    gameThread.resume();
                    gameThread.resume();
                    isPause = false;
                    setKeyBoard();
                }else{
                    gameThread.suspend();
                    countThread.suspend();
                    isPause = true;
                }
            }
        });
    }

    private void setKeyBoard() {
        gameScene.setOnKeyPressed(event -> {
//            System.out.println(event.getCode().getName());
            String key = event.getText().toLowerCase();
            if (key.equals("a") || event.getCode().getName().equals("Left")) {
                shape.goLeft();
                invalidate();
            } else if (key.equals("w") || event.getCode().getName().equals("Up")) {
                shape.change();
                invalidate();
            } else if (key.equals("s") || event.getCode().getName().equals("Down")) {
                shape.goEnd();
                for (Position p : tempPositions) {
                    map[p.getY()][p.getX()] = Config.EMPTY;
                }
                tempPositions.clear();
                shape.getShape()
                        .forEach(position -> map[position.getY()][position.getX()] = getColor(shape));
                clearMap();
                initShape();
            } else if (key.equals("d") || event.getCode().getName().equals("Right")) {
                shape.goRight();
                invalidate();
            } else if (event.getCode().getName().equals("Esc") && isSingle) {
                if (isPause){
                    gameThread.resume();
                    gameThread.resume();
                    isPause = false;
                }else{
                    gameThread.suspend();
                    countThread.suspend();
                    isPause = true;
                    clearKeyBoard();
                }
            }
        });
    }

    private int getColor(BaseShape shape) {
        if (shape instanceof L1)
            return Config.L1;
        else if (shape instanceof L2)
            return Config.L2;
        else if (shape instanceof Line)
            return Config.LINE;
        else if (shape instanceof T)
            return Config.T;
        else if (shape instanceof TI)
            return Config.TI;
        else if (shape instanceof Z1)
            return Config.Z1;
        else if (shape instanceof Z2)
            return Config.Z2;
        else return 1;
    }

    /**
     * 消除整行
     */
    private void clearMap() {
        for (int i = 0; i < Config.y; i++) {
            int j = 0;
            for (; j < Config.x; j++) {
                if (map[i][j] == Config.EMPTY)
                    break;
            }
            if (j == Config.x) {
                map[i][0] = Config.CLEAR;
            }
        }
        for (int i = 0; i < Config.y; i++) {
            if (map[i][0] == Config.CLEAR) {
                score = score + upScore;
                upScore += 1;
                for (int j = i; j > 0; j--) {
                    for (int k = 0; k < Config.x; k++) {
                        map[j][k] = map[j - 1][k];
                    }
                }
                for (int j = 0; j < Config.x; j++) {
                    map[0][j] = Config.EMPTY;
                }
            }
        }
    }
}