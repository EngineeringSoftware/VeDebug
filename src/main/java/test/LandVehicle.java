package test;

/**
 * @author Ben Buhse <bwbuhse@utexas.edu>
 * @author Thomas Wei <thomasw219@gmail.com>
 * @author Zhiqiang Zang <capapoc@gmail.com>
 */
class LandVehicle extends AbstractVehicle {
    LandVehicle() {
        System.out.println("A land vehicle reporting.");
    }

    public void navigate() {
        System.out.println("Navigating on the land.");
    }

    public static LandVehicle of() {
        return new LandVehicle();
    }
}
