Place your trained TFLite model here.

File required: plant_health_model.tflite

Class order (must match training):
  Index 0 → HEALTHY   (vibrant green leaves)
  Index 1 → UNHEALTHY (yellowing, wilting, partially damaged)
  Index 2 → DEAD      (completely dry, no life)

How to get the model:
  1. Run the training script:
       python plant_health_training.py --data_dir ./data --epochs 30 --fine_tune
  2. Copy the output:
       plant_health_training/model/plant_health_model.tflite → this folder

Until the model is placed here, PlantHealthClassifier falls back to an advanced
colour + texture analysis algorithm that still classifies into all 3 categories.
