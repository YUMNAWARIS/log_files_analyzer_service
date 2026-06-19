package main.java.queue;

public class CustomBlockingQueue<V> {

	// ##################
	// # INITIALIZATION #
	// ##################
	private V[] data;
	private int capacity = 10000;
	private int size = 0;
	private int head = 0;
	private int tail = -1;

	/* Constructor */
	@SuppressWarnings("unchecked")
	public CustomBlockingQueue() {
		this.data = (V[]) new Object[this.capacity];
	}

	@SuppressWarnings("unchecked")
	public CustomBlockingQueue(int capacity) {
		this.capacity = capacity;
		this.data = (V[]) new Object[this.capacity];
	}

	private boolean isEmpty() {
		synchronized (this) {
			return this.size == 0;
		}
	}

	private boolean isFull() {
		synchronized (this) {
			return this.size == this.capacity;
		}
	}

	// ##########
	// # ACCESS #
	// ##########

	/* Inserts element to queue (non blocking operation) */
	@SuppressWarnings("unchecked")
	public void insert(V value) {

		synchronized (this) {
			if (this.isFull()) {

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
			this.tail = (this.tail + 1) % this.capacity;
			this.data[this.tail] = value;
			this.size++;
			notifyAll();
		}
	}

	/*
	 * Retrieves element from head of the queue - wait if the queue is empty
	 * (blocking operation)
	 */
	public V retrieve() throws InterruptedException {
		synchronized (this) {
			while (this.isEmpty()) {
				wait();
			}
			V item = this.data[this.head];
			this.head = (this.head + 1) % this.capacity;
			this.size--;
			return item;
		}
	}

	/* Size of the BlockingQueue */
	public int size() {
		synchronized (this) {
			return this.size;
		}
	}

}