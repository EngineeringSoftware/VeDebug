package test;

/**
 * @author Ben Buhse <bwbuhse@utexas.edu>
 * @author Thomas Wei <thomasw219@gmail.com>
 * @author Zhiqiang Zang <capapoc@gmail.com>
 */
public class Utils {

    public static PrismTank pt = new PrismTank();

    public void nonStaticMethod() {}

    public static boolean staticMethodTrue() {
        return true;
    }

    public static boolean staticMethodFalse() {
        return false;
    }

    public static LandVehicle buildLandVehicle() {
        return new LandVehicle();
    }

    public static SeaVehicle buildSeaVehicle() {
        return new SeaVehicle();
    }
}
