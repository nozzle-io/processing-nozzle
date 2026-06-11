import processing.nozzle.*;

NozzleReceiver receiver;

void setup() {
  size(641, 479);
  receiver = new NozzleReceiver(this, "processing-cpu-sender");
}

void draw() {
  println("CPU receive status: " + receiver.update());
  noLoop();
}

void dispose() {
  if (receiver != null) receiver.stop();
}
