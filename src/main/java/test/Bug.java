package test;

/**
 * @author Ben Buhse <bwbuhse@utexas.edu>
 * @author Thomas Wei <thomasw219@gmail.com>
 * @author Zhiqiang Zang <capapoc@gmail.com>
 */
public final class Bug {

    public void call() {
        AbstractVehicle av = ternary();
        // Utils.pt.callUtils();
        // boolean b2 = Utils.staticMethodFalse();
    }

    private AbstractVehicle ternary() {
        return isTrue() ? LandVehicle.of() : SeaVehicle.of();
    }

    public boolean isTrue() {
        return true;
    }
}
