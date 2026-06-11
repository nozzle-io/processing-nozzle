import processing.nozzle.*;

NozzleSender sender;

void setup() {
  size(320, 240);
  sender = new NozzleSender(this, "processing-cpu-sender");
}

void draw() {
  int[] argb = PixelPattern.argb(width, height);
  loadPixels();
  arrayCopy(argb, pixels);
  updatePixels();
  println("CPU publish status: " + sender.publishPixels(argb, width, height));
  noLoop();
}

void dispose() {
  if (sender != null) sender.stop();
}
