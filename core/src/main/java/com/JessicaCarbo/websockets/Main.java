package com.JessicaCarbo.websockets;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.github.czyzby.websocket.WebSocket;
import com.github.czyzby.websocket.WebSockets;
import com.github.czyzby.websocket.WebSocketListener;

public class Main extends ApplicationAdapter {
    private SpriteBatch batch;
    private Texture bg, dogSheet;
    private static final int FRAME_COLS = 5, FRAME_ROWS = 2;
    private FitViewport viewport;
    private Animation<TextureRegion> dogAnimation;

    private float time;
    private float xPos = 4f, yPos = 2.5f; // Posición inicial en el centro
    private float lastSentX, lastSentY;
    private float timeSinceLastSend = 0f;

    private int direction;
    private WebSocket ws;

    // Joystick Virtual y Control
    private Rectangle leftRect, rightRect, upRect, downRect;
    private Vector3 touchPos = new Vector3();

    // IDs únicos para evitar conflictos
    private static final int IDLE = 0, LEFT = 1, RIGHT = 2, UP = 3, DOWN = 4;
    private boolean faceRight = true;

    @Override
    public void create() {
        batch = new SpriteBatch();
        viewport = new FitViewport(8, 5);
        viewport.getCamera().position.set(4f, 2.5f, 0);

        // Definición de zonas del Joystick (Viewport de 8x5)
        leftRect  = new Rectangle(0, 0, 2.6f, 5f);
        rightRect = new Rectangle(5.4f, 0, 2.6f, 5f);
        upRect    = new Rectangle(2.6f, 2.5f, 2.8f, 2.5f);
        downRect  = new Rectangle(2.6f, 0, 2.8f, 2.5f);

        bg = new Texture("background.png");
        dogSheet = new Texture("imageDog.png");

        TextureRegion[][] tmp = TextureRegion.split(dogSheet, dogSheet.getWidth() / FRAME_COLS, dogSheet.getHeight() / FRAME_ROWS);
        TextureRegion[] walkFrames = new TextureRegion[FRAME_COLS * FRAME_ROWS];
        int index = 0;
        for (int i = 0; i < FRAME_ROWS; i++) {
            for (int j = 0; j < FRAME_COLS; j++) {
                walkFrames[index++] = tmp[i][j];
            }
        }
        dogAnimation = new Animation<>(0.08f, walkFrames);
        dogAnimation.setPlayMode(Animation.PlayMode.LOOP);

        connectSocket();
    }

    private void connectSocket() {

        String host = "localhost";

        if (Gdx.app.getType() == Application.ApplicationType.Android) {
            host = "10.0.2.2";
        }

        ws = WebSockets.newSocket(
            WebSockets.toWebSocketUrl(host, 8888)
        );

        ws.setSendGracefully(false);
        ws.addListener(new MyWSListener());
        ws.connect();
    }

    @Override
    public void render() {
        handleInput();
        updateLogic();
        drawFrame();
    }

    private void handleInput() {
        direction = IDLE;

        // Teclado
        if (Gdx.input.isKeyPressed(Input.Keys.RIGHT)) direction = RIGHT;
        else if (Gdx.input.isKeyPressed(Input.Keys.LEFT)) direction = LEFT;
        else if (Gdx.input.isKeyPressed(Input.Keys.UP)) direction = UP;
        else if (Gdx.input.isKeyPressed(Input.Keys.DOWN)) direction = DOWN;
        else direction = virtual_joystick_control();

        // Actualizar orientación visual
        if (direction == LEFT) faceRight = false;
        if (direction == RIGHT) faceRight = true;
    }

    private int virtual_joystick_control() {
        for (int i = 0; i < 5; i++) {
            if (Gdx.input.isTouched(i)) {
                touchPos.set(Gdx.input.getX(i), Gdx.input.getY(i), 0);
                viewport.unproject(touchPos);
                if (upRect.contains(touchPos.x, touchPos.y)) return UP;
                if (downRect.contains(touchPos.x, touchPos.y)) return DOWN;
                if (leftRect.contains(touchPos.x, touchPos.y)) return LEFT;
                if (rightRect.contains(touchPos.x, touchPos.y)) return RIGHT;
            }
        }
        return IDLE;
    }

    private void updateLogic() {
        float dt = Gdx.graphics.getDeltaTime();
        float speed = 2.5f;

        if (direction == LEFT) xPos -= speed * dt;
        if (direction == RIGHT) xPos += speed * dt;
        if (direction == UP) yPos += speed * dt;
        if (direction == DOWN) yPos -= speed * dt;

        time += (direction != IDLE ? dt : 0);
        timeSinceLastSend += dt;

        // Enviar al servidor si nos movemos o si pasa 1 segundo
        boolean moved = Math.abs(xPos - lastSentX) > 0.15f || Math.abs(yPos - lastSentY) > 0.15f;
        if (moved || timeSinceLastSend >= 1.0f) {
            if (ws != null && ws.isOpen()) {
                ws.send("x=" + xPos + ",y=" + yPos);
            }
            lastSentX = xPos;
            lastSentY = yPos;
            timeSinceLastSend = 0;
        }
    }

    private void drawFrame() {
        ScreenUtils.clear(Color.DARK_GRAY);
        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);

        TextureRegion frame = dogAnimation.getKeyFrame(time, true);

        // Lógica de giro (Flip horizontal)
        float scaleX = faceRight ? 1f : -1f;
        float drawX = faceRight ? xPos : xPos + 1f;

        batch.begin();
        batch.draw(bg, 0, 0, viewport.getWorldWidth(), viewport.getWorldHeight());
        batch.draw(frame, drawX, yPos, 0, 0, 1f, 1f, scaleX, 1f, 0f);
        batch.end();
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    @Override
    public void dispose() {
        batch.dispose();
        bg.dispose();
        dogSheet.dispose();
        if (ws != null) ws.close();
    }
}
// COMUNICACIONS (rebuda de missatges)
/////////////////////////////////////////////
class MyWSListener implements WebSocketListener {

    @Override
    public boolean onOpen(WebSocket webSocket) {
        System.out.println("Opening...");
        return false;
    }

    @Override
    public boolean onClose(WebSocket webSocket, int closeCode, String reason) {
        System.out.println("Closing...");
        return false;
    }

    @Override
    public boolean onMessage(WebSocket webSocket, String packet) {
        System.out.println("Message:"+packet);
        return false;
    }

    @Override
    public boolean onMessage(WebSocket webSocket, byte[] packet) {
        System.out.println("Message:"+packet);
        return false;
    }

    @Override
    public boolean onError(WebSocket webSocket, Throwable error) {
        System.out.println("ERROR:"+error.toString());
        return false;
    }
}

