package squidpony.gdx.tests;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ImmediateModeRenderer20;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import squidpony.squidmath.FastNoise;

import static com.badlogic.gdx.Input.Keys.*;
import static com.badlogic.gdx.graphics.GL20.GL_POINTS;

/**
 */
public class FastNoiseVisualizer extends ApplicationAdapter {

    private FastNoise noise = new FastNoise(1);
    private static final int MODE_LIMIT = 1;
    private int mode = 0;
    private int dim = 0; // this can be 0, 1, or 2; add 2 to get the actual dimensions
    private int octaves = 3;
    private float freq = 0.125f;
    private boolean inverse = false;
    private ImmediateModeRenderer20 renderer;
    
    private static final int width = 512, height = 512;

    private InputAdapter input;
    
    private Viewport view;
    private int ctr = -256;
    private boolean keepGoing = true;

    public static float basicPrepare(float n)
    {
        return n * 0.5f + 0.5f;
    }

    @Override
    public void create() {
        renderer = new ImmediateModeRenderer20(width * height, false, true, 0);
        view = new ScreenViewport();

        input = new InputAdapter(){
            @Override
            public boolean keyDown(int keycode) {
                switch (keycode) {
                    case MINUS:
                        mode = (mode + MODE_LIMIT - 1) % MODE_LIMIT;
                        break;
                    case U:
                    case ENTER:
                        if (keycode == ENTER) {
                            mode++;
                            mode %= MODE_LIMIT;
                            ctr = -256;
                        }
                        putMap();
                        break;
                    case P: //pause
                        keepGoing = !keepGoing;
                    case C:
                        ctr++;
                        putMap();
                        break;
                    case E: //earlier seed
                        noise.setSeed(noise.getSeed() - 1);
                        putMap();
                        break;
                    case S: //seed
                        noise.setSeed(noise.getSeed() + 1);
                        putMap();
                        break;
                    case N: // noise type
                        noise.setNoiseType((noise.getNoiseType() + 1) % 10);
                        putMap();
                        break;
                    case D: //dimension
                        dim = (dim + 1) & 3;
                        putMap();
                        break;
                    case F: // frequency
                        noise.setFrequency((float) Math.sin(freq += 0.125f) * 0.25f + 0.25f + 0x1p-7f);
                        putMap();
                        break;
                    case R: // fRactal type
                        noise.setFractalType((noise.getFractalType() + 1) % 3);
                        putMap();
                        break;
                    case H: // higher octaves
                        noise.setFractalOctaves((octaves = octaves + 1 & 7) + 1);
                        putMap();
                        break;
                    case L: // lower octaves
                        noise.setFractalOctaves((octaves = octaves + 7 & 7) + 1);
                        putMap();
                        break;
                    case I: // inverse mode
                        if (inverse = !inverse) {
                            noise.setFractalLacunarity(0.5f);
                            noise.setFractalGain(2f);
                        } else {
                            noise.setFractalLacunarity(2f);
                            noise.setFractalGain(0.5f);
                        }
                        putMap();
                        break;
                    case K: // sKip
                        ctr += 1000;
                        putMap();
                        break;
                    case Q:
                    case ESCAPE: {
                        Gdx.app.exit();
                    }
                }
                return true;
            }
        };
        Gdx.input.setInputProcessor(input);
    }

    public void putMap() {
        renderer.begin(view.getCamera().combined, GL_POINTS);
        float bright;
        switch (dim) {
            case 0:
                for (int x = 0; x < 512; x++) {
                    for (int y = 0; y < 512; y++) {
                        bright = basicPrepare(noise.getConfiguredNoise(x + ctr, y + ctr));
                        renderer.color(bright, bright, bright, 1f);
                        renderer.vertex(x, y, 0);
                    }
                }
                break;
            case 1:
                for (int x = 0; x < 512; x++) {
                    for (int y = 0; y < 512; y++) {
                        bright = basicPrepare(noise.getConfiguredNoise(x, y, ctr));
                        renderer.color(bright, bright, bright, 1f);
                        renderer.vertex(x, y, 0);
                    }
                }
                break;
            case 2:
                for (int x = 0; x < 512; x++) {
                    for (int y = 0; y < 512; y++) {
                        bright = basicPrepare(noise.getConfiguredNoise(x, y, ctr, 0x1p-4f * (x + y - ctr)));
                        renderer.color(bright, bright, bright, 1f);
                        renderer.vertex(x, y, 0);
                    }
                }
                break;
            case 3:
                float xx, yy;
                for (int x = 0; x < 512; x++) {
                    xx = x * 0x1p-4f;
                    for (int y = 0; y < 512; y++) {
                        yy = y * 0x1p-4f;
                        bright = basicPrepare(noise.getConfiguredNoise(
                                ctr + xx, x + ctr, y - ctr,
                                ctr - yy, x +  yy, y - xx));
                        renderer.color(bright, bright, bright, 1f);
                        renderer.vertex(x, y, 0);
                    }
                }
                break;
        }
        renderer.end();

    }

    @Override
    public void render() {
        // not sure if this is always needed...
        Gdx.gl.glDisable(GL20.GL_BLEND);
        Gdx.graphics.setTitle(String.valueOf(Gdx.graphics.getFramesPerSecond()));
        if (keepGoing) {
            // standard clear the background routine for libGDX
            Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            ctr++;
            putMap();
        }
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        view.update(width, height, true);
        view.apply(true);
    }

    public static void main(String[] arg) {
        LwjglApplicationConfiguration config = new LwjglApplicationConfiguration();
        config.title = "SquidLib Test: Hash Visualization";
        config.width = width;
        config.height = height;
        config.foregroundFPS = 0;
        config.vSyncEnabled = false;
        config.resizable = false;
        config.addIcon("Tentacle-16.png", Files.FileType.Internal);
        config.addIcon("Tentacle-32.png", Files.FileType.Internal);
        config.addIcon("Tentacle-128.png", Files.FileType.Internal);
        new LwjglApplication(new FastNoiseVisualizer(), config);
    }
}
