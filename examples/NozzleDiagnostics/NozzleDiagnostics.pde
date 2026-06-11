import processing.nozzle.*;

void setup() {
  size(320, 240);
  println(Nozzle.diagnostics(this).summary());
  exit();
}
