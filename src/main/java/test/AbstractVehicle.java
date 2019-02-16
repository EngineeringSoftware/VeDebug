package test;

/**
 * @author Ben Buhse <bwbuhse@utexas.edu>
 * @author Thomas Wei <thomasw219@gmail.com>
 * @author Zhiqiang Zang <capapoc@gmail.com>
 */
abstract class AbstractVehicle {
    public abstract void navigate();

    public boolean isOrganic() {
        return false;
    }

    public boolean isMovable() {
        return true;
    }
}
