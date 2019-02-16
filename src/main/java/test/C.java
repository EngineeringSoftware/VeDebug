package test;

import nottest.D;
 
/**
 * This class is just used for certain testing.
 *
 * @author Ben Buhse <bwbuhse@utexas.edu>
 * @author Thomas Wei <thomasw219@gmail.com>
 * @author Zhiqiang Zang <capapoc@gmail.com>
 */
public class C {

    private int i;

    public C() {
        this.i = 5;
    }

    public static void m() {
        System.out.println("Hola mundo");
    }

    private static int test(int i) {
        return i * i;
    }

    private static void test2(int j) {
        System.out.println(j * j);
    }

    public static void main(String[] args){
        Bug bug = new Bug();
        bug.call();
    }

    public String toString() {
        return "Howdy";
    }
}
