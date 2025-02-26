package utils;

import applet.HotswapGuiSketch;
import processing.core.PVector;

public class CameraGrid extends HotswapGuiSketch {
    private PVector cameraOffset;
    private PVector playerPos = new PVector();

    public static void main(String[] args) {
        HotswapGuiSketch.main("utils.CameraGrid");
    }

    public void settings() {
        size(800, 800, P2D);
    }

    public void setup() {
        cameraOffset = new PVector(width / 2, height / 2);
    }

    public void draw() {
        float t = radians(frameCount);
        background(150);
        updateCamera();
        drawGridAroundPlayer();
        drawPlayer();
        playerPos.add(new PVector(noise(t), noise(10+t)).setMag(3));
    }

    private void updateCamera() {
        float cameraFollowTightness = .05f;
        cameraOffset.x = lerp(cameraOffset.x, width * .5f - playerPos.x, cameraFollowTightness);
        cameraOffset.y = lerp(cameraOffset.y, height * .5f - playerPos.y, cameraFollowTightness);
        translate(cameraOffset.x, cameraOffset.y);
    }

    private void drawGridAroundPlayer() {
        float cellSize = 40;
        float bufferZone = cellSize*2;  //set this to -cellSize*2 to see how it works
        float xEdge = width*.5f+bufferZone;
        float yEdge = height*.5f+bufferZone;
        pushMatrix();
        PVector gridOffset = new PVector(playerPos.x%cellSize, playerPos.y%cellSize);
        translate(playerPos.x-gridOffset.x, playerPos.y-gridOffset.y);
        for (float x = -xEdge; x <= xEdge; x += cellSize) {
            line(x, -yEdge, x, yEdge);
        }
        for (float y = -yEdge; y <= yEdge; y += cellSize) {
            line(-xEdge, y, xEdge, y);
        }
        popMatrix();
    }

    private void drawPlayer() {
        ellipse(playerPos.x, playerPos.y, 20, 20);
    }

}
