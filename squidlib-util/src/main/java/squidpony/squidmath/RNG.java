package squidpony.squidmath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.Random;

/**
 * A wrapper class for working with random number generators in a more friendly
 * way.
 *
 * Includes methods for getting values between two numbers and for getting
 * random elements from a collection or array.
 *
 * @author Eben Howard - http://squidpony.com - howard@squidpony.com
 */
public class RNG {

    protected static final double DOUBLE_UNIT = 0x1.0p-53; // 1.0 / (1L << 53)
    protected RandomnessSource random;
    protected double nextNextGaussian;
    protected boolean haveNextNextGaussian = false;
    private Random ran = null;

    /**
     * Default constructor uses Mersenne Twister, which is of high quality and
     * fairly high speed.
     */
    public RNG() {
        this(new MersenneTwister());
    }

    /**
     * Uses the provided source of randomness for all calculations. This
     * constructor should be used if setting the seed is needed as the provided
     * source of randomness can be seeded as desired before passing it in.
     *
     * @param random the source of randomness
     */
    public RNG(RandomnessSource random) {
        this.random = random;
    }

    /**
     * @return a Random instance that can be used for legacy compatability
     */
    public Random asRandom() {
        if (ran == null) {
            ran = new Random() {
                @Override
                protected int next(int bits) {
                    return super.next(bits);
                }
            };
        }
        return ran;
    }

    /**
     * Returns a value from a even distribution from min (inclusive) to max
     * (exclusive).
     *
     * @param min the minimum bound on the return value (inclusive)
     * @param max the maximum bound on the return value (exclusive)
     * @return the found value
     */
    public double between(double min, double max) {
        return min + (max - min) * nextDouble();
    }

    /**
     * Returns a value between min (inclusive) and max (exclusive).
     *
     * The inclusive and exclusive behavior is to match the behavior of the
     * similar method that deals with floating point values.
     *
     * @param min the minimum bound on the return value (inclusive)
     * @param max the maximum bound on the return value (exclusive)
     * @return the found value
     */
    public int between(int min, int max) {
        return nextInt(max - min) + min;
    }

    /**
     * Returns the average of a number of randomly selected numbers from the
     * provided range, with min being inclusive and max being exclusive. It will
     * sample the number of times passed in as the third parameter.
     *
     * The inclusive and exclusive behavior is to match the behavior of the
     * similar method that deals with floating point values.
     *
     * This can be used to weight RNG calls to the average between min and max.
     *
     * @param min the minimum bound on the return value (inclusive)
     * @param max the maximum bound on the return value (exclusive)
     * @param samples the number of samples to take
     * @return the found value
     */
    public int betweenWeighted(int min, int max, int samples) {
        int sum = 0;
        for (int i = 0; i < samples; i++) {
            sum += between(min, max);
        }

        int answer = Math.round((float) sum / samples);
        return answer;
    }

    /**
     * Returns a random element from the provided array and maintains object
     * type.
     *
     * @param <T> the type of the returned object
     * @param array the array to get an element from
     * @return the randomly selected element
     */
    public <T> T getRandomElement(T[] array) {
        if (array.length < 1) {
            return null;
        }
        return array[nextInt(array.length)];
    }

    /**
     * Returns a random element from the provided list. If the list is empty
     * then null is returned.
     *
     * @param <T> the type of the returned object
     * @param list the list to get an element from
     * @return the randomly selected element
     */
    public <T> T getRandomElement(List<T> list) {
        if (list.size() <= 0) {
            return null;
        }
        return list.get(nextInt(list.size()));
    }

    /**
     * Returns a random elements from the provided queue. If the queue is empty
     * then null is returned.
     * 
	 * <p>
	 * Beware that this method allocates a copy of {@code list}, hence it's
	 * quite costly.
	 * </p>
     *
     * @param <T> the type of the returned object
     * @param list the list to get an element from
     * @return the randomly selected element
     */
    public <T> T getRandomElement(Queue<T> list) {
        if (list.isEmpty()) {
            return null;
        }
        return (T) list.toArray()[nextInt(list.size())];
    }

	/**
     * Given a {@link List} l, this selects a random element of l to be the first value in the returned list l2. It
     * retains the order of elements in l after that random element and makes them follow the first element in l2, and
     * loops around to use elements from the start of l after it has placed the last element of l into l2.
     *
     * Essentially, it does what it says on the tin. It randomly rotates the List l.
     *
	 * @param l
	 *            A {@link List} that will not be modified by this method. All elements of this parameter will be
     *            shared with the returned List.
     * @param <T> No restrictions on type. Changes to elements of the returned List will be reflected in the parameter.
     * @return A shallow copy of {@code l} that has been rotated so its first element has been randomly chosen
     * from all possible elements but order is retained. Will "loop around" to contain element 0 of l after the last
     * element of l, then element 1, etc.
	 */
	public <T> List<T> randomRotation(final List<T> l) {
		final int sz = l.size();
		if (sz == 0)
			return Collections.<T>emptyList();

		/*
		 * This returned an Iterable before, but it seems like this should be functionally equivalent.
		 * Collections.rotate should prefer the best-performing way to rotate l, which would be an in-place
		 * modification for ArrayLists and an append to a sublist for Lists that don't support efficient random access.
		 */
        List<T> l2 = new ArrayList<T>(l);
        Collections.rotate(l2, nextInt(sz));
        return l2;
	}

    /**
     * Exactly equivalent to {@code randomRotation}.
     * @param list
     *            A {@link List} that will not be modified by this method. All elements of this parameter will be
     *            shared with the returned List.
     * @param <T> No restrictions on type. Changes to elements of the returned List will be reflected in the parameter.
     * @return A shallow copy of {@code list} that has been rotated so its first element has been randomly chosen
     * from all possible elements but order is retained. Will "loop around" to contain element 0 of list after the last
     * element of list, then element 1, etc.
     */
    public <T> List<T> getRandomStartIterable(final List<T> list) {
        return randomRotation(list);
    }

    /**
     * Shuffle an array using the Fisher-Yates algorithm.
     * @param elements an array of T; will not be modified
     * @param <T> can be any non-primitive type.
     * @return a shuffled copy of elements
     */
    public <T> T[] shuffle(T[] elements)
    {
        T[] array = elements.clone();
        int n = array.length;
        for (int i = 0; i < n; i++)
        {
            int r = i + nextInt(n - i);
            T t = array[r];
            array[r] = array[i];
            array[i] = t;
        }
        return array;
    }
    /**
     * Shuffle a {@link List} using the Fisher-Yates algorithm.
     * @param elements an array of T; will not be modified
     * @param <T> can be any non-primitive type.
     * @return a shuffled ArrayList containing the whole of elements in pseudo-random order.
     */
    public <T> ArrayList<T> shuffle(List<T> elements)
    {
        ArrayList<T> al = new ArrayList<T>(elements);
        int n = al.size();
        for (int i = 0; i < n; i++)
        {
            Collections.swap(al, i + nextInt(n - i), i);
        }
        return al;
    }
    /**
     * @return a value from the gaussian distribution
     */
    public synchronized double nextGaussian() {
        if (haveNextNextGaussian) {
            haveNextNextGaussian = false;
            return nextNextGaussian;
        } else {
            double v1, v2, s;
            do {
                v1 = 2 * nextDouble() - 1; // between -1 and 1
                v2 = 2 * nextDouble() - 1; // between -1 and 1
                s = v1 * v1 + v2 * v2;
            } while (s >= 1 || s == 0);
            double multiplier = StrictMath.sqrt(-2 * StrictMath.log(s) / s);
            nextNextGaussian = v2 * multiplier;
            haveNextNextGaussian = true;
            return v1 * multiplier;
        }
    }

    /**
     * This returns a maximum of 0.9999999999999999 because that is the largest
     * Double value that is less than 1.0
     *
     * @return a value between 0 (inclusive) and 0.9999999999999999 (inclusive)
     */
    public double nextDouble() {
        return (((long) (next(26)) << 27) + next(27)) * DOUBLE_UNIT;
    }

    /**
     * This returns a random double between 0.0 (inclusive) and max (exclusive).
     *
     * @return a value between 0 (inclusive) and max (exclusive)
     */
    public double nextDouble(double max) {
        return (((long) (next(26)) << 27) + next(27)) * DOUBLE_UNIT * max;
    }

    /**
     * This returns a maximum of 0.99999994 because that is the largest Float
     * value that is less than 1.0f
     *
     * @return a value between 0 (inclusive) and 0.99999994 (inclusive)
     */
    public float nextFloat() {
        return next(24) / ((float) (1 << 24));
    }

    public boolean nextBoolean() {
        return next(1) != 0;
    }

    public long nextLong() {
        return ((long) (next(32)) << 32) + next(32);
    }

    /**
     * Returns a random integer below the given bound, or 0 if the bound is 0 or
     * negative.
     *
     * @param bound the upper bound (exclusive)
     * @return the found number
     */
    public int nextInt(int bound) {
        if (bound <= 0) {
            return 0;
        }

        int r = next(31);
        int m = bound - 1;
        if ((bound & m) == 0) { // i.e., bound is a power of 2
            r = (int) ((bound * (long) r) >> 31);
        } else {
            for (int u = r; u - (r = u % bound) + m < 0; u = next(31)) {
            }
        }
        return r;
    }

    public int nextInt() {
        return next(32);
    }

    public int next(int bits) {
        return random.next(bits);
    }

    public RandomnessSource getRandomness() {
        return random;
    }

    public void setRandomness(RandomnessSource random) {
        this.random = random;
    }


}
