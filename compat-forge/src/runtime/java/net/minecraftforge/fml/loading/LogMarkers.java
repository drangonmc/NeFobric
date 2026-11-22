package net.minecraftforge.fml.loading;

import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

/** FML 加载阶段日志用的标记。 */
public final class LogMarkers {
    public static final Marker CORE = MarkerFactory.getMarker("CORE");
    public static final Marker LOADING = MarkerFactory.getMarker("LOADING");
    public static final Marker SCAN = MarkerFactory.getMarker("SCAN");
    public static final Marker SPLASH = MarkerFactory.getMarker("SPLASH");

    private LogMarkers() {
    }
}
