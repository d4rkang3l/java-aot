package libgame;

import libcore.CPP;
import libcore.CPPMethod;

public class SDL {
    static public void init() {
        SDLApi.init();
    }

    static public SDLWindow createWindow(String title, int width, int height) {
        return new SDLWindow(SDLApi.createWindow(title, width, height));
    }

    static public void delay(int ms) {
        SDLApi.delay(ms);
    }
}