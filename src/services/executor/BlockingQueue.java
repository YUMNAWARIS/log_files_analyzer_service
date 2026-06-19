package services.executor;

/**
 * Thread-safe FIFO queue. Coordination for blocking {@link #retrieve()} calls
 * is the caller's responsibility (see {@link ExecutorService}).
 */
public class BlockingQueue<V> {

    private static final int DEFAULT_CAPACITY = 16;

    private V[] data;
    private int capacity;
    private int size;
    private int head;
    private int tail;

    @SuppressWarnings("unchecked")
    public BlockingQueue() {
        this(DEFAULT_CAPACITY);
    }

    @SuppressWarnings("unchecked")
    public BlockingQueue(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive.");
        }
        this.capacity = capacity;
        this.data = (V[]) new Object[capacity];
        this.head = 0;
        this.tail = -1;
        this.size = 0;
    }

    public void insert(V value) {
        synchronized (this) {
            if (size == capacity) {
                grow();
            }
            tail = (tail + 1) % capacity;
            data[tail] = value;
            size++;
        }
    }

    public V retrieve() {
        synchronized (this) {
            if (size == 0) {
                throw new IllegalStateException("Cannot retrieve from an empty queue.");
            }
            V value = data[head];
            data[head] = null;
            head = (head + 1) % capacity;
            size--;
            return value;
        }
    }

    public synchronized int size() {
        return size;
    }

    public synchronized boolean isEmpty() {
        return size == 0;
    }

    @SuppressWarnings("unchecked")
    private void grow() {
        int newCapacity = capacity * 2;
        V[] newData = (V[]) new Object[newCapacity];
        for (int i = 0; i < size; i++) {
            newData[i] = data[(head + i) % capacity];
        }
        data = newData;
        head = 0;
        tail = size - 1;
        capacity = newCapacity;
    }

}
