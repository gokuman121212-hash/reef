import cv2
import mediapipe as mp
from mediapipe.tasks import python
from mediapipe.tasks.python import vision
import pyautogui
import math
import time
import os
import urllib.request

# Configuración de PyAutoGUI
pyautogui.FAILSAFE = False
screen_width, screen_height = pyautogui.size()

# Descargar automáticamente el modelo Hand Landmarker si no existe
model_path = 'hand_landmarker.task'
if not os.path.exists(model_path):
    print("Descargando el modelo de detección de manos de MediaPipe...")
    model_url = "https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task"
    urllib.request.urlretrieve(model_url, model_path)
    print("¡Modelo descargado con éxito!")

# Configurar el detector usando la API moderna de Tasks
base_options = python.BaseOptions(model_asset_path=model_path)
options = vision.HandLandmarkerOptions(
    base_options=base_options,
    num_hands=1,
    min_hand_detection_confidence=0.7,
    min_hand_presence_confidence=0.7,
    min_tracking_confidence=0.7
)
detector = vision.HandLandmarker.create_from_options(options)

# Inicializar la cámara web
cap = cv2.VideoCapture(0)
cap.set(cv2.CAP_PROP_FRAME_WIDTH, 640)
cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 480)

prev_x, prev_y = 0, 0
smooth_factor = 5
is_pinched = False
last_scroll_time = 0
scroll_cooldown = 0.05
prev_index_y = 0

print("Sistema de control por gestos iniciado. Presiona 'Esc' para salir.")

while cap.isOpened():
    success, frame = cap.read()
    if not success:
        break

    frame = cv2.flip(frame, 1)
    h, w, _ = frame.shape
    
    # Convertir el fotograma de OpenCV (BGR) a MediaPipe Image (RGB)
    rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
    mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb_frame)
    
    # Detección con HandLandmarker
    detection_result = detector.detect(mp_image)

    if detection_result.hand_landmarks:
        for hand_landmarks in detection_result.hand_landmarks:
            # Dibujar landmarks manualmente o extraer puntos clave
            # Landmarks: 4 (Pulgar), 8 (Índice)
            thumb_tip = hand_landmarks[4]
            index_tip = hand_landmarks[8]
            
            # Dibujar puntos en pantalla con OpenCV
            for lm in hand_landmarks:
                cx, cy = int(lm.x * w), int(lm.y * h)
                cv2.circle(frame, (cx, cy), 3, (0, 255, 0), -1)

            # Control de movimiento del cursor con el dedo índice
            x = int(index_tip.x * screen_width)
            y = int(index_tip.y * screen_height)
            
            curr_x = prev_x + (x - prev_x) / smooth_factor
            curr_y = prev_y + (y - prev_y) / smooth_factor
            
            pyautogui.moveTo(curr_x, curr_y)
            prev_x, prev_y = curr_x, curr_y
            
            # Gesto de clic (Pellizco entre pulgar e índice)
            distance_pinch = math.hypot(
                (thumb_tip.x - index_tip.x) * w,
                (thumb_tip.y - index_tip.y) * h
            )
            
            if distance_pinch < 35:
                if not is_pinched:
                    pyautogui.click()
                    is_pinched = True
            else:
                is_pinched = False
                
            # Control de scroll vertical
            current_time = time.time()
            if current_time - last_scroll_time > scroll_cooldown:
                delta_y = index_tip.y - prev_index_y
                if abs(delta_y) > 0.04:
                    if delta_y > 0:
                        pyautogui.scroll(-120)
                    else:
                        pyautogui.scroll(120)
                    last_scroll_time = current_time
            
            prev_index_y = index_tip.y

    cv2.imshow("CAFQ Vision Control - Desktop", frame)
    
    if cv2.waitKey(1) & 0xFF == 27:
        break

cap.release()
cv2.destroyAllWindows()