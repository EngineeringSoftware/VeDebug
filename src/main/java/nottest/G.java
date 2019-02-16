package nottest;

import test.C;

/**
 * @author Ben Buhse <bwbuhse@utexas.edu>
 * @author Thomas Wei <thomasw219@gmail.com>
 * @author Zhiqiang Zang <capapoc@gmail.com>
 */
public class G {
    public static void s() {
        C.m();

        C c = new C();
        String str = c.toString();
    }
}
