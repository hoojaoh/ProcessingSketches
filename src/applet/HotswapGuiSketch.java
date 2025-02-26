package applet;

import processing.core.PConstants;
import processing.core.PGraphics;
import processing.opengl.PShader;

import java.io.File;
import java.util.ArrayList;

import static java.lang.System.currentTimeMillis;

/**
 * A sketch extending this class can apply changes to shaders as you edit the shader file
 *
 * - use uniform() to get a reference to the shader file in order to pass uniforms to it
 * - use hotFilter() and hotShader() to apply your last compilable shader as filter or shader respectively
 * - no need to call loadShader() manually at all
 *
 * - to see the effects you have to actually change the last modified timestamp of the file, (try CTRL+S)
 * - the results of any compilation errors will be printed to standard processing console
 * - only really supports fragment shaders, vert shader support will be shaky at best
 *
 * //TODO delve into vertex shaders and fix the prototype with shader-school
 */
public abstract class HotswapGuiSketch extends GuiSketch {

    ArrayList<ShaderSnapshot> snapshots = new ArrayList<ShaderSnapshot>();
    int refreshRateInMillis = 36;

    protected void chromaticAberrationPass(PGraphics pg) {
        String chromatic = "postFX\\chromaticAberrationFrag.glsl";
        uniform(chromatic).set("maxDistort", slider("chromatic", 5));
        hotFilter(chromatic, pg);
    }

    protected void patternPass(PGraphics pg, float t){
        String pattern = "pattern.glsl";
        uniform(pattern).set("time", t);
        hotFilter(pattern, pg);
    }

    protected void ceilBlack(PGraphics pg){
        String ceilAlpha = "ceilBlack.glsl";
        hotFilter(ceilAlpha,pg);
    }

    protected void alphaFade(PGraphics pg) {
        pg.hint(PConstants.DISABLE_DEPTH_TEST);
        pg.pushStyle();
        pg.blendMode(SUBTRACT);
        pg.noStroke();
        pg.fill(255,slider("alpha", 0, 100,17));
        pg.rectMode(CENTER);
        pg.rect(0,0,width*2, height*2);
        pg.hint(PConstants.ENABLE_DEPTH_TEST);
        pg.popStyle();
    }

    protected void noiseOffsetPass(PGraphics pg, float t) {
        String noiseOffset = "noiseOffset.glsl";
        uniform(noiseOffset).set("time", t);
        uniform(noiseOffset).set("mixAmt", slider("mix", 0,1,.1f));
        uniform(noiseOffset).set("mag", slider("mag", 0,.01f, .001f));
        uniform(noiseOffset).set("frq", slider("frq", 0, 50, 8.5f));
        hotFilter(noiseOffset, pg);
    }

    protected void noisePass(float t, PGraphics pg) {
        String noise = "postFX/noiseFrag.glsl";
        uniform(noise).set("time", t);
        uniform(noise).set("amount", slider("noise amt", 0, .24f, .05f));
        uniform(noise).set("speed", slider("noise spd", 1));
        hotFilter(noise, pg);
    }

    protected void rgbSplitUniformPass(PGraphics pg) {
        String rgbSplit = "rgbSplitUniform.glsl";
        uniform(rgbSplit).set("delta", slider("delta", 2));
        hotFilter(rgbSplit, pg);
    }

    protected void rgbSplitPass(PGraphics pg) {
        String rgbSplit = "postFX/rgbSplitFrag.glsl";
        uniform(rgbSplit).set("delta", slider("rgb mag",100));
        hotFilter(rgbSplit, pg);
    }

    protected void saturationVibrancePass(PGraphics pg) {
        String saturationVibrance = "postFX/saturationVibranceFrag.glsl";
        uniform(saturationVibrance).set("saturation", slider("saturation", 0, 0.5f, 0));
        uniform(saturationVibrance).set("vibrance", slider("vibrance", 0, 0.5f, 0));
        hotFilter(saturationVibrance, pg);
    }

    protected void toonPass(PGraphics pg){
        String toonPass = "postFX/toonFrag.glsl";
        hotFilter(toonPass, pg);
    }


    protected void brightnessContractFrag(PGraphics pg){
        String brightnessContractPass = "postFX/brightnessContrastFrag.glsl";
        uniform(brightnessContractPass).set("brightness", slider("brightness", 1, false));
        uniform(brightnessContractPass).set("contrast", slider("contrast", 2));
        hotFilter(brightnessContractPass, pg);
    }

    protected void vignettePass(PGraphics pg){
        String vignettePass = "postFX/vignetteFrag.glsl";
        uniform(vignettePass).set("amount", slider("vignette", 5));
        uniform(vignettePass).set("falloff", slider("falloff"));
        hotFilter(vignettePass, pg);
    }

    public PShader uniform(String fragPath) {
        ShaderSnapshot snapshot = findSnapshotByPath(fragPath);
        snapshot = initIfNull(snapshot, fragPath, null);
        return snapshot.compiledShader;
    }

    public PShader uniform(String fragPath, String vertPath) {
        ShaderSnapshot snapshot = findSnapshotByPath(fragPath);
        snapshot = initIfNull(snapshot, fragPath, vertPath);
        return snapshot.compiledShader;
    }

    public void hotFilter(String path, PGraphics canvas) {
        hotShader(path, null, true, canvas);
    }

    public void hotFilter(String path) {
        hotShader(path, null, true, g);
    }

    public void hotShader(String fragPath, String vertPath, PGraphics canvas) {
        hotShader(fragPath, vertPath, false, canvas);
    }

    public void hotShader(String fragPath, String vertPath) {
        hotShader(fragPath, vertPath, false, g);
    }

    public void hotShader(String fragPath, PGraphics canvas) {
        hotShader(fragPath,null, false, canvas);
    }

    public void hotShader(String fragPath) {
        hotShader(fragPath,null, false, g);
    }

    private void hotShader(String fragPath, String vertPath, boolean filter, PGraphics canvas) {
        ShaderSnapshot snapshot = findSnapshotByPath(fragPath);
        snapshot = initIfNull(snapshot, fragPath, vertPath);
        snapshot.update(filter, canvas);
    }

    private ShaderSnapshot initIfNull(ShaderSnapshot snapshot, String fragPath, String vertPath) {
        if (snapshot == null) {
            snapshot = new ShaderSnapshot(fragPath, vertPath);
            snapshots.add(snapshot);
        }
        return snapshot;
    }

    private ShaderSnapshot findSnapshotByPath(String path) {
        for (ShaderSnapshot snapshot : snapshots) {
            if (snapshot.fragPath.equals(path)) {
                return snapshot;
            }
        }
        return null;
    }

    class ShaderSnapshot {
        String fragPath;
        String vertPath;
        File fragFile;
        File vertFile;
        PShader compiledShader;
        long fragLastKnownModified, vertLastKnownModified, lastChecked;
        boolean compiledAtLeastOnce = false;
        long lastKnownUncompilable = -refreshRateInMillis;


        ShaderSnapshot(String fragPath, String vertPath) {
            if(vertPath != null){
                compiledShader = loadShader(fragPath, vertPath);
                vertFile = dataFile(vertPath);
                vertLastKnownModified = vertFile.lastModified();
                if (!vertFile.isFile()) {
                    println("Could not find shader at " + vertFile.getPath());
                }
            }else{
                compiledShader = loadShader(fragPath);
            }
            fragFile = dataFile(fragPath);
            fragLastKnownModified = fragFile.lastModified();
            lastChecked = currentTimeMillis();
            if (!fragFile.isFile()) {
                println("Could not find shader at " + fragFile.getPath());
            }
            this.fragPath = fragPath;
            this.vertPath = vertPath;
        }

        long max(long a, long b){
            if(a > b){
                return a;
            }
            return b;
        }

        void update(boolean filter, PGraphics pg) {
            long currentTimeMillis = currentTimeMillis();
            long lastModified = fragFile.lastModified();
            if(vertFile != null){
                lastModified = max(lastModified, vertFile.lastModified());
            }
            if (compiledAtLeastOnce && currentTimeMillis < lastChecked + refreshRateInMillis) {
//                println("compiled at least once, not checking, standard apply");
                applyShader(compiledShader, filter, pg);
                return;
            }
            if(!compiledAtLeastOnce && lastModified > lastKnownUncompilable){
//                println("first try");
                tryCompileNewVersion(filter, pg, lastModified);
                return;
            }
            lastChecked = currentTimeMillis;
            if (lastModified > fragLastKnownModified && lastModified > lastKnownUncompilable) {
//                println("file changed, repeat try");
                tryCompileNewVersion(filter, pg, lastModified);
            } else if(compiledAtLeastOnce) {
//                println("file didn't change, standard apply");
                applyShader(compiledShader, filter, pg);
            }
        }

        private void applyShader(PShader shader, boolean filter, PGraphics pg) {
            if (filter) {
                pg.filter(shader);
            } else {
                pg.shader(shader);
            }
        }

        private void tryCompileNewVersion(boolean filter, PGraphics pg, long lastModified){
            try {
                PShader candidate;
                if(vertFile == null){
                    candidate = loadShader(fragPath);
                }else{
                    candidate = loadShader(fragPath, vertPath);
                }
                // we need to call filter() or shader() here in order to catch any compilation errors and not halt the sketch
                applyShader(candidate, filter, pg);
                compiledShader = candidate;
                compiledAtLeastOnce = true;
                fragLastKnownModified = lastModified;
            } catch (Exception ex) {
                lastKnownUncompilable = lastModified;
                println("\n" + fragFile.getName() + ": " + ex.getMessage());
            }
        }
    }
}
