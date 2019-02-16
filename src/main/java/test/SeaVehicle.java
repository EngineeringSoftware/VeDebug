package test;

/**
 * @author Ben Buhse <bwbuhse@utexas.edu>
 * @author Thomas Wei <thomasw219@gmail.com>
 * @author Zhiqiang Zang <capapoc@gmail.com>
 */
class SeaVehicle extends AbstractVehicle{
    SeaVehicle() {
        System.out.println("A sea vehicle reporting.");
    }

    public void navigate() {
        System.out.println("Navigating on the sea.");
    }

    public static SeaVehicle of() {
        return new SeaVehicle();
    }
}
