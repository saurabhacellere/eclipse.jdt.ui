//5, 22 -> 5, 27   AllowLoadtime == false
package p;
class A {
	private static final int CONSTANT= 2 + 3;

	void f() {
		int i= 1 - (CONSTANT);
	}
}