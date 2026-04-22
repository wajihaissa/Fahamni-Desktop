package tn.esprit.fahamni.room3d;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.TextureKey;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.light.PointLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Ray;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.shadow.CompareMode;
import com.jme3.shadow.DirectionalLightShadowRenderer;
import com.jme3.shadow.EdgeFilteringMode;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Quad;
import com.jme3.collision.CollisionResults;
import com.jme3.texture.Texture;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class Room3DApplication extends SimpleApplication {

    private static final String SELECT_SEAT_MAPPING = "select-room-seat";
    private static final String QUICK_SELECT_SEAT_MAPPING = "quick-select-room-seat";
    private static final String SEAT_LABEL_KEY = "seatLabel";
    private static final String SEAT_STATUS_KEY = "seatStatus";
    private static final float CLICK_DRAG_THRESHOLD = 8f;
    private static final float HOVERED_SEAT_SCALE = 1.03f;
    private static final float SELECTED_SEAT_SCALE = 1.08f;
    private static final float CAMERA_TRANSITION_DURATION_SECONDS = 0.55f;
    private static final float CAMERA_BUTTON_WIDTH = 144f;
    private static final float CAMERA_BUTTON_HEIGHT = 28f;
    private static final float CAMERA_BUTTON_GAP = 10f;
    private static final float CAMERA_BUTTON_MARGIN_TOP = 18f;
    private static final float CAMERA_BUTTON_MARGIN_RIGHT = 18f;
    private static final float CAMERA_BUTTON_LABEL_PADDING_X = 12f;
    private static final float CAMERA_BUTTON_LABEL_BASELINE = 20f;
    private static final ColorRGBA CAMERA_BUTTON_DEFAULT_COLOR = new ColorRGBA(0.9f, 0.94f, 0.98f, 0.92f);
    private static final ColorRGBA CAMERA_BUTTON_HOVER_COLOR = new ColorRGBA(0.76f, 0.86f, 0.97f, 0.96f);
    private static final ColorRGBA CAMERA_BUTTON_ACTIVE_COLOR = new ColorRGBA(0.18f, 0.42f, 0.72f, 0.98f);
    private static final ColorRGBA CAMERA_BUTTON_TEXT_COLOR = new ColorRGBA(0.2f, 0.28f, 0.38f, 1f);

    private static final float WALL_HEIGHT = 3.8f;
    private static final float CEILING_THICKNESS = 0.05f;
    private static final float FLOOR_THICKNESS = 0.08f;
    private static final float FRONT_BORDER_HEIGHT = 0.22f;
    private static final float SHOWCASE_CAMERA_FOV = 42f;
    private static final float TEACHER_CAMERA_FOV = 88f;
    private static final String FLOOR_TEXTURE_PATH = "com/fahamni/room3d/textures/floor-concrete-light.png";
    private static final String WALL_TEXTURE_PATH = "com/fahamni/room3d/textures/wall-plaster-soft.png";
    private static final String WOOD_TEXTURE_PATH = "com/fahamni/room3d/textures/desk-oak-light.png";

    private Room3DPreviewData previewData;
    private Node sceneRoot;
    private Geometry ceilingCutaway;
    private Node ceilingLightPanels;
    private BitmapFont hudFont;
    private BitmapText titleText;
    private BitmapText summaryText;
    private BitmapText selectionText;
    private BitmapText legendText;
    private BitmapText interactionHintText;
    private BitmapText hoverText;
    private boolean initialized;
    private DirectionalLightShadowRenderer shadowRenderer;
    private DirectionalLight primaryShadowLight;

    private Material floorMaterial;
    private Material wallMaterial;
    private Material ceilingMaterial;
    private Material deskMaterial;
    private Material stageMaterial;
    private Material trimMaterial;
    private Material metalMaterial;
    private Material glassMaterial;
    private Material lightPanelMaterial;
    private Material screenMaterial;
    private Material boardMaterial;
    private Material storageMaterial;
    private Material accentMaterial;
    private Material availableSeatMaterial;
    private Material reservedSeatMaterial;
    private Material maintenanceSeatMaterial;
    private Material unavailableSeatMaterial;
    private Material hoverIndicatorMaterial;
    private Material selectedIndicatorMaterial;

    private final Map<Node, SeatVisual> seatVisuals = new HashMap<>();
    private final Map<CameraPreset, CameraPresetButton> cameraPresetButtons = new EnumMap<>(CameraPreset.class);
    private Node hoveredSeatNode;
    private Node selectedSeatNode;
    private Vector2f primaryClickStart;
    private boolean primarySelectionArmed;
    private volatile Integer selectedSeatIdSnapshot;
    private volatile String selectedSeatLabelSnapshot;
    private LayoutMetrics activeLayoutMetrics;
    private CameraPreset activeCameraPreset = CameraPreset.ENTRANCE;
    private CameraPreset hoveredCameraPreset;
    private CameraTransition activeCameraTransition;
    private float activeCameraTransitionElapsed;
    private float activeCameraFov = SHOWCASE_CAMERA_FOV;

    private final ActionListener selectionListener = (name, isPressed, tpf) -> {
        if (sceneRoot == null) {
            return;
        }

        if (SELECT_SEAT_MAPPING.equals(name)) {
            handlePrimarySeatInteraction(isPressed);
            return;
        }

        if (QUICK_SELECT_SEAT_MAPPING.equals(name) && isPressed) {
            if (findCameraPresetAtCursor(inputManager.getCursorPosition()) != null) {
                return;
            }
            selectSeat(findSeatNodeAtCursor());
        }
    };

    public Room3DApplication(Room3DPreviewData previewData) {
        this.previewData = Objects.requireNonNull(previewData, "previewData");
    }

    @Override
    public void simpleInitApp() {
        initialized = true;
        setDisplayFps(false);
        setDisplayStatView(false);
        viewPort.setBackgroundColor(new ColorRGBA(0.9f, 0.91f, 0.92f, 1f));

        flyCam.setMoveSpeed(14f);
        flyCam.setDragToRotate(true);

        inputManager.addMapping(SELECT_SEAT_MAPPING, new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        inputManager.addMapping(QUICK_SELECT_SEAT_MAPPING, new MouseButtonTrigger(MouseInput.BUTTON_RIGHT));
        inputManager.addListener(selectionListener, SELECT_SEAT_MAPPING, QUICK_SELECT_SEAT_MAPPING);

        hudFont = assetManager.loadFont("Interface/Fonts/Default.fnt");
        initialiseMaterials();
        initialiseShadows();
        rebuildScene();
    }

    @Override
    public void simpleUpdate(float tpf) {
        updateCameraPresetHover();
        updateHoveredSeat();
        updateCameraTransition(tpf);
    }

    @Override
    public void reshape(int width, int height) {
        super.reshape(width, height);
        updateCameraLens();
        updateHudPositions();
    }

    @Override
    public void destroy() {
        if (shadowRenderer != null) {
            viewPort.removeProcessor(shadowRenderer);
            shadowRenderer = null;
        }
        Room3DViewerLauncher.onApplicationClosed(this);
        super.destroy();
    }

    public void updatePreview(Room3DPreviewData previewData) {
        this.previewData = Objects.requireNonNull(previewData, "previewData");
        if (initialized) {
            rebuildScene();
        }
    }

    public boolean isSelectionMode() {
        return previewData != null && previewData.supportsSeatSelection();
    }

    public Integer getSelectedSeatId() {
        return selectedSeatIdSnapshot;
    }

    public String getSelectedSeatLabel() {
        return selectedSeatLabelSnapshot;
    }

    private void rebuildScene() {
        if (sceneRoot != null) {
            rootNode.detachChild(sceneRoot);
        }
        guiNode.detachAllChildren();
        seatVisuals.clear();
        cameraPresetButtons.clear();
        hoveredSeatNode = null;
        selectedSeatNode = null;
        primaryClickStart = null;
        primarySelectionArmed = false;
        selectedSeatIdSnapshot = null;
        selectedSeatLabelSnapshot = null;
        activeLayoutMetrics = null;
        activeCameraPreset = CameraPreset.ENTRANCE;
        hoveredCameraPreset = null;
        activeCameraTransition = null;
        activeCameraTransitionElapsed = 0f;
        activeCameraFov = SHOWCASE_CAMERA_FOV;
        ceilingCutaway = null;
        ceilingLightPanels = null;

        initialiseHud();

        sceneRoot = new Node("room-3d-preview-root");
        rootNode.attachChild(sceneRoot);

        LayoutMetrics metrics = buildLayoutMetrics(previewData);

        sceneRoot.attachChild(createFloorPlinth(metrics));
        sceneRoot.attachChild(createFloor(metrics));
        ceilingCutaway = createCeiling(metrics);
        sceneRoot.attachChild(ceilingCutaway);
        attachWalls(metrics);
        attachArchitecturalDetails(metrics);
        attachTeachingArea(metrics);
        attachSeats(metrics);
        attachCeilingLights(metrics);
        attachLights();
        positionCamera(metrics);

        titleText.setText(previewData.roomName());
        summaryText.setText(buildDefaultSummary());
        selectionText.setText(buildDefaultSelectionText());
        selectionText.setColor(createHudNeutralColor());
        legendText.setText(buildLegendText());
        interactionHintText.setText(buildInteractionHintText());
        hideHoverText();
        updateHudPositions();
    }

    private void initialiseHud() {
        titleText = new BitmapText(hudFont);
        titleText.setSize(hudFont.getCharSet().getRenderedSize() * 1.45f);
        titleText.setColor(ColorRGBA.Black);

        summaryText = new BitmapText(hudFont);
        summaryText.setSize(hudFont.getCharSet().getRenderedSize());
        summaryText.setColor(new ColorRGBA(0.12f, 0.18f, 0.28f, 1f));

        selectionText = new BitmapText(hudFont);
        selectionText.setSize(hudFont.getCharSet().getRenderedSize() * 0.98f);
        selectionText.setColor(createHudNeutralColor());

        legendText = new BitmapText(hudFont);
        legendText.setSize(hudFont.getCharSet().getRenderedSize() * 0.86f);
        legendText.setColor(new ColorRGBA(0.23f, 0.29f, 0.38f, 1f));

        interactionHintText = new BitmapText(hudFont);
        interactionHintText.setSize(hudFont.getCharSet().getRenderedSize() * 0.9f);
        interactionHintText.setColor(new ColorRGBA(0.28f, 0.34f, 0.44f, 1f));

        hoverText = new BitmapText(hudFont);
        hoverText.setSize(hudFont.getCharSet().getRenderedSize() * 0.92f);
        hoverText.setColor(ColorRGBA.White);
        hoverText.setCullHint(Spatial.CullHint.Always);

        initialiseCameraPresetButtons();

        guiNode.attachChild(titleText);
        guiNode.attachChild(summaryText);
        guiNode.attachChild(selectionText);
        guiNode.attachChild(legendText);
        guiNode.attachChild(interactionHintText);
        guiNode.attachChild(hoverText);
    }

    private void initialiseCameraPresetButtons() {
        cameraPresetButtons.clear();

        for (CameraPreset preset : CameraPreset.values()) {
            CameraPresetButton button = createCameraPresetButton(preset);
            cameraPresetButtons.put(preset, button);
            guiNode.attachChild(button.node());
        }

        updateCameraPresetButtonStyles();
    }

    private void updateHudPositions() {
        if (titleText == null || summaryText == null || selectionText == null
            || legendText == null || interactionHintText == null || hoverText == null) {
            return;
        }

        float topMargin = cam.getHeight() - 18f;
        titleText.setLocalTranslation(18f, topMargin, 0f);
        summaryText.setLocalTranslation(18f, topMargin - titleText.getLineHeight() - 8f, 0f);
        selectionText.setLocalTranslation(18f, summaryText.getLocalTranslation().y - summaryText.getLineHeight() - 6f, 0f);
        interactionHintText.setLocalTranslation(18f, 24f + interactionHintText.getLineHeight(), 0f);
        legendText.setLocalTranslation(18f, interactionHintText.getLocalTranslation().y + legendText.getLineHeight() + 10f, 0f);
        updateCameraPresetButtonPositions();
        updateHoverTextPosition();
    }

    private CameraPresetButton createCameraPresetButton(CameraPreset preset) {
        Node buttonNode = new Node("camera-preset-button-" + preset.name().toLowerCase(Locale.ROOT));

        Geometry background = new Geometry(buttonNode.getName() + "-background", new Quad(CAMERA_BUTTON_WIDTH, CAMERA_BUTTON_HEIGHT));
        background.setMaterial(createFlatMaterial(CAMERA_BUTTON_DEFAULT_COLOR.clone()));
        background.setQueueBucket(RenderQueue.Bucket.Gui);

        BitmapText label = new BitmapText(hudFont);
        label.setSize(hudFont.getCharSet().getRenderedSize() * 0.84f);
        label.setColor(CAMERA_BUTTON_TEXT_COLOR);
        label.setText(preset.label());
        label.setLocalTranslation(CAMERA_BUTTON_LABEL_PADDING_X, CAMERA_BUTTON_LABEL_BASELINE, 0f);

        buttonNode.attachChild(background);
        buttonNode.attachChild(label);
        return new CameraPresetButton(preset, buttonNode, background, label, CAMERA_BUTTON_WIDTH, CAMERA_BUTTON_HEIGHT);
    }

    private void updateCameraPresetButtonPositions() {
        if (cameraPresetButtons.isEmpty()) {
            return;
        }

        float totalWidth = (CAMERA_BUTTON_WIDTH * 2f) + CAMERA_BUTTON_GAP;
        float startX = Math.max(18f, cam.getWidth() - CAMERA_BUTTON_MARGIN_RIGHT - totalWidth);
        float startY = cam.getHeight() - CAMERA_BUTTON_MARGIN_TOP - CAMERA_BUTTON_HEIGHT;

        for (CameraPreset preset : CameraPreset.values()) {
            CameraPresetButton button = cameraPresetButtons.get(preset);
            if (button == null) {
                continue;
            }

            float x = startX + (preset.column() * (CAMERA_BUTTON_WIDTH + CAMERA_BUTTON_GAP));
            float y = startY - (preset.row() * (CAMERA_BUTTON_HEIGHT + CAMERA_BUTTON_GAP));
            button.node().setLocalTranslation(x, y, 0f);
        }
    }

    private void updateCameraPresetButtonStyles() {
        for (CameraPreset preset : CameraPreset.values()) {
            CameraPresetButton button = cameraPresetButtons.get(preset);
            if (button == null) {
                continue;
            }

            boolean active = preset == activeCameraPreset;
            boolean hovered = preset == hoveredCameraPreset;

            ColorRGBA backgroundColor = active
                ? CAMERA_BUTTON_ACTIVE_COLOR
                : hovered ? CAMERA_BUTTON_HOVER_COLOR : CAMERA_BUTTON_DEFAULT_COLOR;
            ColorRGBA labelColor = active ? ColorRGBA.White : CAMERA_BUTTON_TEXT_COLOR;

            button.background().getMaterial().setColor("Color", backgroundColor);
            button.label().setColor(labelColor);
        }
    }

    private void initialiseMaterials() {
        floorMaterial = createTexturedLitMaterial(FLOOR_TEXTURE_PATH, new ColorRGBA(0.94f, 0.94f, 0.94f, 1f), 0.62f, 6f);
        wallMaterial = createTexturedLitMaterial(WALL_TEXTURE_PATH, new ColorRGBA(0.99f, 0.99f, 0.98f, 1f), 0.72f, 3f);
        ceilingMaterial = createTexturedLitMaterial(WALL_TEXTURE_PATH, new ColorRGBA(1f, 1f, 0.995f, 1f), 0.76f, 2f);
        deskMaterial = createTexturedLitMaterial(WOOD_TEXTURE_PATH, new ColorRGBA(0.97f, 0.93f, 0.86f, 1f), 0.58f, 14f);
        stageMaterial = createTexturedLitMaterial(FLOOR_TEXTURE_PATH, new ColorRGBA(0.97f, 0.97f, 0.96f, 1f), 0.65f, 4f);
        trimMaterial = createLitMaterial(new ColorRGBA(0.85f, 0.87f, 0.9f, 1f));
        metalMaterial = createLitMaterial(new ColorRGBA(0.28f, 0.31f, 0.36f, 1f));
        glassMaterial = createLitMaterial(new ColorRGBA(0.9f, 0.95f, 0.99f, 1f));
        lightPanelMaterial = createFlatMaterial(new ColorRGBA(1f, 0.99f, 0.97f, 1f));
        screenMaterial = createFlatMaterial(new ColorRGBA(0.17f, 0.19f, 0.23f, 1f));
        boardMaterial = createLitMaterial(new ColorRGBA(0.99f, 0.99f, 0.98f, 1f));
        storageMaterial = createTexturedLitMaterial(WALL_TEXTURE_PATH, new ColorRGBA(0.985f, 0.985f, 0.98f, 1f), 0.7f, 4f);
        accentMaterial = createFlatMaterial(new ColorRGBA(0.72f, 0.85f, 0.92f, 1f));
        availableSeatMaterial = createLitMaterial(new ColorRGBA(0.24f, 0.34f, 0.56f, 1f));
        reservedSeatMaterial = createLitMaterial(new ColorRGBA(0.72f, 0.42f, 0.31f, 1f));
        maintenanceSeatMaterial = createLitMaterial(new ColorRGBA(0.69f, 0.58f, 0.24f, 1f));
        unavailableSeatMaterial = createLitMaterial(new ColorRGBA(0.5f, 0.54f, 0.6f, 1f));
        hoverIndicatorMaterial = createFlatMaterial(new ColorRGBA(0.36f, 0.67f, 0.9f, 0.88f));
        selectedIndicatorMaterial = createFlatMaterial(new ColorRGBA(0.14f, 0.44f, 0.86f, 0.92f));
    }

    private Material createLitMaterial(ColorRGBA baseColor) {
        Material material = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        material.setBoolean("UseMaterialColors", true);
        material.setColor("Diffuse", baseColor);
        material.setColor("Ambient", baseColor.mult(0.7f));
        material.setColor("Specular", ColorRGBA.White.mult(0.3f));
        material.setFloat("Shininess", 8f);
        return material;
    }

    private Material createTexturedLitMaterial(String texturePath, ColorRGBA baseColor, float ambientFactor, float shininess) {
        Material material = createLitMaterial(baseColor);
        TextureKey textureKey = new TextureKey(texturePath, false);
        textureKey.setGenerateMips(true);
        textureKey.setAnisotropy(16);
        Texture texture = assetManager.loadTexture(textureKey);
        texture.setWrap(Texture.WrapMode.Repeat);
        texture.setMinFilter(Texture.MinFilter.Trilinear);
        texture.setMagFilter(Texture.MagFilter.Bilinear);
        texture.setAnisotropicFilter(16);
        material.setTexture("DiffuseMap", texture);
        material.setColor("Ambient", baseColor.mult(ambientFactor));
        material.setFloat("Shininess", shininess);
        return material;
    }

    private Material createFlatMaterial(ColorRGBA color) {
        Material material = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        material.setColor("Color", color);
        return material;
    }

    private void initialiseShadows() {
        if (shadowRenderer != null) {
            viewPort.removeProcessor(shadowRenderer);
        }

        shadowRenderer = new DirectionalLightShadowRenderer(assetManager, 4096, 4);
        shadowRenderer.setLambda(0.58f);
        shadowRenderer.setShadowIntensity(0.48f);
        shadowRenderer.setEdgeFilteringMode(EdgeFilteringMode.PCF8);
        shadowRenderer.setShadowCompareMode(CompareMode.Hardware);
        shadowRenderer.setEnabledStabilization(true);
        shadowRenderer.setShadowZExtend(48f);
        shadowRenderer.setShadowZFadeLength(10f);
        shadowRenderer.setEdgesThickness(3);
        viewPort.addProcessor(shadowRenderer);
    }

    private void updateCameraLens() {
        if (cam == null || cam.getHeight() <= 0) {
            return;
        }

        float aspect = (float) cam.getWidth() / (float) cam.getHeight();
        cam.setFrustumPerspective(activeCameraFov, aspect, 0.05f, Math.max(350f, cam.getFrustumFar()));
    }

    private Geometry createFloor(LayoutMetrics metrics) {
        Box floorMesh = new Box(metrics.roomWidth() / 2f, FLOOR_THICKNESS, metrics.roomDepth() / 2f);
        floorMesh.scaleTextureCoordinates(new Vector2f(
            Math.max(3f, metrics.roomWidth() * 0.55f),
            Math.max(3f, metrics.roomDepth() * 0.55f)
        ));
        Geometry floor = new Geometry(
            "room-floor",
            floorMesh
        );
        floor.setMaterial(floorMaterial);
        floor.setShadowMode(RenderQueue.ShadowMode.Receive);
        floor.setLocalTranslation(0f, -FLOOR_THICKNESS, 0f);
        return floor;
    }

    private Geometry createFloorPlinth(LayoutMetrics metrics) {
        Box plinthMesh = new Box((metrics.roomWidth() / 2f) + 0.42f, 0.15f, (metrics.roomDepth() / 2f) + 0.42f);
        plinthMesh.scaleTextureCoordinates(new Vector2f(
            Math.max(3f, metrics.roomWidth() * 0.45f),
            Math.max(3f, metrics.roomDepth() * 0.45f)
        ));
        Geometry plinth = new Geometry(
            "room-floor-plinth",
            plinthMesh
        );
        plinth.setMaterial(storageMaterial);
        plinth.setShadowMode(RenderQueue.ShadowMode.Receive);
        plinth.setLocalTranslation(0f, -0.17f, 0f);
        return plinth;
    }

    private Geometry createCeiling(LayoutMetrics metrics) {
        float ceilingDepth = metrics.roomDepth() * 0.68f;
        float ceilingCenterZ = -(metrics.roomDepth() - ceilingDepth) / 2f;
        Box ceilingMesh = new Box(metrics.roomWidth() / 2f, CEILING_THICKNESS, ceilingDepth / 2f);
        ceilingMesh.scaleTextureCoordinates(new Vector2f(
            Math.max(2f, metrics.roomWidth() * 0.36f),
            Math.max(2f, ceilingDepth * 0.36f)
        ));
        Geometry ceiling = new Geometry(
            "room-ceiling-cutaway",
            ceilingMesh
        );
        ceiling.setMaterial(ceilingMaterial);
        ceiling.setShadowMode(RenderQueue.ShadowMode.Receive);
        ceiling.setLocalTranslation(0f, WALL_HEIGHT, ceilingCenterZ);
        return ceiling;
    }

    private void attachWalls(LayoutMetrics metrics) {
        float wallThickness = 0.08f;
        float halfRoomWidth = metrics.roomWidth() / 2f;
        float halfRoomDepth = metrics.roomDepth() / 2f;

        sceneRoot.attachChild(createWall(
            "back-wall",
            metrics.roomWidth(),
            WALL_HEIGHT,
            wallThickness,
            new Vector3f(0f, WALL_HEIGHT / 2f - FLOOR_THICKNESS, -halfRoomDepth)
        ));
        sceneRoot.attachChild(createWall(
            "left-wall",
            wallThickness,
            WALL_HEIGHT,
            metrics.roomDepth(),
            new Vector3f(-halfRoomWidth, WALL_HEIGHT / 2f - FLOOR_THICKNESS, 0f)
        ));
        sceneRoot.attachChild(createWall(
            "right-wall",
            wallThickness,
            WALL_HEIGHT,
            metrics.roomDepth(),
            new Vector3f(halfRoomWidth, WALL_HEIGHT / 2f - FLOOR_THICKNESS, 0f)
        ));
        sceneRoot.attachChild(createWall(
            "front-border",
            metrics.roomWidth(),
            FRONT_BORDER_HEIGHT,
            wallThickness,
            new Vector3f(0f, FRONT_BORDER_HEIGHT / 2f - FLOOR_THICKNESS, halfRoomDepth)
        ));
    }

    private void attachArchitecturalDetails(LayoutMetrics metrics) {
        float halfRoomWidth = metrics.roomWidth() / 2f;
        float halfRoomDepth = metrics.roomDepth() / 2f;
        float frameOffset = 0.01f;

        sceneRoot.attachChild(createSimpleBox(
            "ceiling-cornice",
            metrics.roomWidth() * 0.48f,
            0.08f,
            0.04f,
            trimMaterial,
            new Vector3f(0f, WALL_HEIGHT - 0.32f, -halfRoomDepth + 0.08f)
        ));

        for (int index = 0; index < 7; index++) {
            float windowZ = -metrics.roomDepth() * 0.18f + (index * 0.9f);
            String rightKey = "right-window-" + index;

            sceneRoot.attachChild(createSimpleBox(
                rightKey,
                0.02f,
                0.72f,
                0.32f,
                glassMaterial,
                new Vector3f(halfRoomWidth - 0.045f, 1.92f, windowZ)
            ));
            sceneRoot.attachChild(createSimpleBox(
                rightKey + "-frame",
                0.032f,
                0.8f,
                0.36f,
                trimMaterial,
                new Vector3f(halfRoomWidth - 0.022f, 1.92f, windowZ)
            ));
        }

        sceneRoot.attachChild(createSimpleBox(
            "wall-poster-frame",
            0.04f,
            0.5f,
            0.78f,
            trimMaterial,
            new Vector3f(-halfRoomWidth + 0.045f, 1.78f, -metrics.roomDepth() * 0.18f)
        ));
        sceneRoot.attachChild(createSimpleBox(
            "wall-poster-art",
            0.018f,
            0.44f,
            0.7f,
            accentMaterial,
            new Vector3f(-halfRoomWidth + 0.068f, 1.78f, -metrics.roomDepth() * 0.18f)
        ));

        sceneRoot.attachChild(createSimpleBox(
            "door",
            0.46f,
            1.0f,
            0.03f,
            storageMaterial,
            new Vector3f(-halfRoomWidth + 0.88f, 1.0f, -halfRoomDepth + 0.07f)
        ));
        sceneRoot.attachChild(createSimpleBox(
            "door-handle",
            0.024f,
            0.024f,
            0.015f,
            metalMaterial,
            new Vector3f(-halfRoomWidth + 0.62f, 0.92f, -halfRoomDepth + 0.11f)
        ));

        sceneRoot.attachChild(createSimpleBox(
            "front-border-trim",
            metrics.roomWidth(),
            0.03f,
            0.05f,
            trimMaterial,
            new Vector3f(0f, FRONT_BORDER_HEIGHT + frameOffset, halfRoomDepth)
        ));
        sceneRoot.attachChild(createSimpleBox(
            "room-baseboard-left",
            0.03f,
            0.09f,
            metrics.roomDepth(),
            trimMaterial,
            new Vector3f(-halfRoomWidth + 0.01f, 0.09f, 0f)
        ));
        sceneRoot.attachChild(createSimpleBox(
            "room-baseboard-back",
            metrics.roomWidth(),
            0.09f,
            0.03f,
            trimMaterial,
            new Vector3f(0f, 0.09f, -halfRoomDepth + 0.01f)
        ));
    }

    private Geometry createWall(String name, float width, float height, float depth, Vector3f position) {
        Box wallMesh = new Box(width / 2f, height / 2f, depth / 2f);
        wallMesh.scaleTextureCoordinates(new Vector2f(
            Math.max(2f, width * 0.42f),
            Math.max(2f, Math.max(height, depth) * 0.42f)
        ));
        Geometry wall = new Geometry(name, wallMesh);
        wall.setMaterial(wallMaterial);
        wall.setShadowMode(RenderQueue.ShadowMode.Receive);
        wall.setLocalTranslation(position);
        return wall;
    }

    private void attachTeachingArea(LayoutMetrics metrics) {
        float frontZoneZ = metrics.frontSeatZ() + 1.55f;
        String disposition = normalize(previewData.disposition());
        sceneRoot.attachChild(createSimpleBox(
            "teaching-platform",
            Math.max(2.8f, metrics.layoutWidth() + 1.6f),
            0.05f,
            1.25f,
            stageMaterial,
            new Vector3f(0f, 0f, frontZoneZ + 0.45f)
        ));

        Node presentationScreen = createPresentationScreen();
        presentationScreen.setLocalTranslation(0f, 0f, frontZoneZ + 0.88f);
        sceneRoot.attachChild(presentationScreen);

        if ("reunion".equals(disposition)) {
            Node centralTable = createTableNode(
                "central-table",
                Math.max(2.5f, metrics.layoutWidth() * 0.55f),
                Math.max(1.15f, metrics.layoutDepth() * 0.35f),
                0.76f,
                deskMaterial
            );
            centralTable.setLocalTranslation(0f, 0f, 0f);
            centralTable.attachChild(createSimpleBox(
                "conference-hub",
                0.18f,
                0.03f,
                0.18f,
                metalMaterial,
                new Vector3f(0f, 0.81f, 0f)
            ));
            sceneRoot.attachChild(centralTable);
        } else {
            Node desk = createTeacherDeskNode();
            desk.setLocalTranslation(-Math.max(1.45f, metrics.layoutWidth() * 0.38f), 0f, frontZoneZ - 0.12f);
            sceneRoot.attachChild(desk);

            Node storageShelf = createStorageShelfNode();
            storageShelf.setLocalTranslation(Math.max(1.65f, metrics.layoutWidth() * 0.44f), 0f, frontZoneZ + 0.16f);
            sceneRoot.attachChild(storageShelf);
        }
    }

    private void attachSeats(LayoutMetrics metrics) {
        String disposition = normalize(previewData.disposition());

        for (Room3DPreviewData.SeatPreview seat : previewData.seats()) {
            float x = ((seat.column() - metrics.minColumn()) * metrics.seatSpacingX()) - (metrics.layoutWidth() / 2f);
            float z = metrics.frontSeatZ() - ((seat.row() - metrics.minRow()) * metrics.seatSpacingZ());

            Node seatNode = createSeatNode(seat, disposition);
            seatNode.setLocalTranslation(x, 0f, z);

            if ("u".equals(disposition) || "reunion".equals(disposition)) {
                seatNode.lookAt(new Vector3f(0f, 0.65f, 0f), Vector3f.UNIT_Y);
            }

            sceneRoot.attachChild(seatNode);
        }
    }

    private Node createSeatNode(Room3DPreviewData.SeatPreview seat, String disposition) {
        Node seatNode = new Node("seat-" + seat.number());
        seatNode.setUserData(SEAT_LABEL_KEY, seat.label());
        seatNode.setUserData(SEAT_STATUS_KEY, seat.state().displayLabel());

        Material seatMaterial = resolveSeatMaterial(seat.state());

        seatNode.attachChild(createSimpleBox(
            "seat-base-" + seat.number(),
            0.22f,
            0.035f,
            0.22f,
            seatMaterial,
            new Vector3f(0f, 0.43f, 0f)
        ));
        seatNode.attachChild(createSimpleBox(
            "seat-back-" + seat.number(),
            0.22f,
            0.22f,
            0.03f,
            seatMaterial,
            new Vector3f(0f, 0.7f, -0.18f)
        ));
        seatNode.attachChild(createSimpleBox(
            "seat-support-" + seat.number(),
            0.03f,
            0.18f,
            0.03f,
            metalMaterial,
            new Vector3f(0f, 0.19f, -0.02f)
        ));
        seatNode.attachChild(createChairLeg("seat-leg-fl-" + seat.number(), 0.17f, -0.14f));
        seatNode.attachChild(createChairLeg("seat-leg-fr-" + seat.number(), -0.17f, -0.14f));
        seatNode.attachChild(createChairLeg("seat-leg-bl-" + seat.number(), 0.17f, 0.14f));
        seatNode.attachChild(createChairLeg("seat-leg-br-" + seat.number(), -0.17f, 0.14f));

        if (!"reunion".equals(disposition)) {
            Node deskNode = createSeatDeskNode(seat.number(), disposition);
            deskNode.setLocalTranslation(0f, 0f, 0.6f);
            seatNode.attachChild(deskNode);
        }

        Geometry interactionIndicator = createSeatIndicator(seat.number());
        seatNode.attachChild(interactionIndicator);
        seatVisuals.put(seatNode, new SeatVisual(seat, interactionIndicator));

        return seatNode;
    }

    private Geometry createChairLeg(String name, float x, float z) {
        return createSimpleBox(name, 0.018f, 0.2f, 0.018f, metalMaterial, new Vector3f(x, 0.19f, z));
    }

    private Geometry createSeatIndicator(int seatNumber) {
        Geometry indicator = new Geometry("seat-indicator-" + seatNumber, new Box(0.52f, 0.012f, 0.52f));
        indicator.setMaterial(hoverIndicatorMaterial);
        indicator.setShadowMode(RenderQueue.ShadowMode.Off);
        indicator.setLocalTranslation(0f, 0.015f, 0f);
        indicator.setCullHint(Spatial.CullHint.Always);
        return indicator;
    }

    private Node createSeatDeskNode(int seatNumber, String disposition) {
        Node deskNode;
        if ("informatique".equals(disposition)) {
            deskNode = createTableNode("lab-desk-" + seatNumber, 0.82f, 0.5f, 0.73f, deskMaterial);
            deskNode.attachChild(createSimpleBox(
                "monitor-" + seatNumber,
                0.16f,
                0.1f,
                0.025f,
                screenMaterial,
                new Vector3f(0f, 0.9f, 0.1f)
            ));
            deskNode.attachChild(createSimpleBox(
                "monitor-stand-" + seatNumber,
                0.02f,
                0.06f,
                0.025f,
                metalMaterial,
                new Vector3f(0f, 0.8f, 0.12f)
            ));
            deskNode.attachChild(createSimpleBox(
                "keyboard-" + seatNumber,
                0.16f,
                0.01f,
                0.07f,
                metalMaterial,
                new Vector3f(0f, 0.76f, -0.06f)
            ));
            return deskNode;
        }

        if ("conference".equals(disposition) || "u".equals(disposition)) {
            deskNode = createTableNode("conference-desk-" + seatNumber, 0.72f, 0.46f, 0.73f, deskMaterial);
            deskNode.attachChild(createSimpleBox(
                "conference-pad-" + seatNumber,
                0.15f,
                0.01f,
                0.1f,
                trimMaterial,
                new Vector3f(0f, 0.76f, 0.02f)
            ));
            return deskNode;
        }

        deskNode = createTableNode("class-desk-" + seatNumber, 0.72f, 0.42f, 0.72f, deskMaterial);
        deskNode.attachChild(createSimpleBox(
            "class-book-" + seatNumber,
            0.14f,
            0.018f,
            0.1f,
            trimMaterial,
            new Vector3f(0f, 0.75f, 0.02f)
        ));
        return deskNode;
    }

    private Node createTableNode(String name, float width, float depth, float topHeight, Material topMaterial) {
        Node tableNode = new Node(name);
        float topHalfHeight = 0.022f;
        float legInsetX = Math.max(0.08f, width / 2f - 0.09f);
        float legInsetZ = Math.max(0.08f, depth / 2f - 0.09f);
        float legHalfHeight = Math.max(0.24f, (topHeight - (topHalfHeight * 2f)) / 2f);
        float legCenterY = legHalfHeight;

        tableNode.attachChild(createSimpleBox(
            name + "-top",
            width / 2f,
            topHalfHeight,
            depth / 2f,
            topMaterial,
            new Vector3f(0f, topHeight, 0f)
        ));
        tableNode.attachChild(createSimpleBox(
            name + "-leg-1",
            0.018f,
            legHalfHeight,
            0.018f,
            metalMaterial,
            new Vector3f(legInsetX, legCenterY, legInsetZ)
        ));
        tableNode.attachChild(createSimpleBox(
            name + "-leg-2",
            0.018f,
            legHalfHeight,
            0.018f,
            metalMaterial,
            new Vector3f(-legInsetX, legCenterY, legInsetZ)
        ));
        tableNode.attachChild(createSimpleBox(
            name + "-leg-3",
            0.018f,
            legHalfHeight,
            0.018f,
            metalMaterial,
            new Vector3f(legInsetX, legCenterY, -legInsetZ)
        ));
        tableNode.attachChild(createSimpleBox(
            name + "-leg-4",
            0.018f,
            legHalfHeight,
            0.018f,
            metalMaterial,
            new Vector3f(-legInsetX, legCenterY, -legInsetZ)
        ));
        return tableNode;
    }

    private Node createTeacherDeskNode() {
        Node deskNode = createTableNode("teacher-desk", 0.98f, 0.56f, 0.75f, storageMaterial);
        deskNode.attachChild(createSimpleBox(
            "teacher-desk-device",
            0.16f,
            0.012f,
            0.12f,
            screenMaterial,
            new Vector3f(0.12f, 0.79f, -0.02f)
        ));
        deskNode.attachChild(createSimpleBox(
            "teacher-desk-cup",
            0.028f,
            0.05f,
            0.028f,
            metalMaterial,
            new Vector3f(-0.2f, 0.81f, 0.08f)
        ));
        return deskNode;
    }

    private Node createStorageShelfNode() {
        Node shelfNode = new Node("teacher-storage-shelf");
        shelfNode.attachChild(createSimpleBox(
            "teacher-storage-body",
            0.42f,
            0.7f,
            0.2f,
            storageMaterial,
            new Vector3f(0f, 0.7f, 0f)
        ));
        shelfNode.attachChild(createSimpleBox(
            "teacher-storage-shelf-top",
            0.45f,
            0.02f,
            0.22f,
            trimMaterial,
            new Vector3f(0f, 1.42f, 0f)
        ));
        shelfNode.attachChild(createSimpleBox(
            "teacher-storage-mid",
            0.38f,
            0.016f,
            0.18f,
            trimMaterial,
            new Vector3f(0f, 0.92f, 0f)
        ));
        shelfNode.attachChild(createSimpleBox(
            "teacher-storage-books-1",
            0.06f,
            0.14f,
            0.05f,
            availableSeatMaterial,
            new Vector3f(-0.18f, 0.66f, 0.08f)
        ));
        shelfNode.attachChild(createSimpleBox(
            "teacher-storage-books-2",
            0.06f,
            0.12f,
            0.05f,
            reservedSeatMaterial,
            new Vector3f(-0.04f, 0.64f, 0.08f)
        ));
        shelfNode.attachChild(createSimpleBox(
            "teacher-storage-box",
            0.1f,
            0.07f,
            0.09f,
            metalMaterial,
            new Vector3f(0.18f, 1.48f, 0f)
        ));
        shelfNode.attachChild(createSimpleBox(
            "teacher-storage-plant-pot",
            0.06f,
            0.05f,
            0.06f,
            deskMaterial,
            new Vector3f(-0.18f, 1.48f, 0f)
        ));
        shelfNode.attachChild(createSimpleBox(
            "teacher-storage-plant-leaf-1",
            0.03f,
            0.1f,
            0.03f,
            createLitMaterial(new ColorRGBA(0.52f, 0.7f, 0.48f, 1f)),
            new Vector3f(-0.22f, 1.62f, 0f)
        ));
        shelfNode.attachChild(createSimpleBox(
            "teacher-storage-plant-leaf-2",
            0.03f,
            0.08f,
            0.03f,
            createLitMaterial(new ColorRGBA(0.58f, 0.74f, 0.54f, 1f)),
            new Vector3f(-0.15f, 1.58f, 0.04f)
        ));
        return shelfNode;
    }

    private Node createPresentationScreen() {
        Node screenNode = new Node("presentation-screen");
        screenNode.attachChild(createSimpleBox(
            "board-frame-center",
            1.32f,
            0.72f,
            0.05f,
            trimMaterial,
            new Vector3f(0f, 2.02f, 0f)
        ));
        screenNode.attachChild(createSimpleBox(
            "board-surface-center",
            1.22f,
            0.64f,
            0.03f,
            boardMaterial,
            new Vector3f(0f, 2.02f, 0.015f)
        ));
        screenNode.attachChild(createSimpleBox(
            "board-surface-left",
            0.42f,
            0.62f,
            0.04f,
            boardMaterial,
            new Vector3f(-1.05f, 2.0f, 0.02f)
        ));
        screenNode.attachChild(createSimpleBox(
            "board-surface-right",
            0.42f,
            0.62f,
            0.04f,
            boardMaterial,
            new Vector3f(1.05f, 2.0f, 0.02f)
        ));
        screenNode.attachChild(createSimpleBox(
            "board-tray",
            1.26f,
            0.025f,
            0.08f,
            metalMaterial,
            new Vector3f(0f, 1.26f, 0.08f)
        ));
        screenNode.attachChild(createSimpleBox(
            "board-projector-bar",
            0.6f,
            0.03f,
            0.05f,
            trimMaterial,
            new Vector3f(0f, 2.95f, 0.03f)
        ));
        screenNode.attachChild(createSimpleBox(
            "board-clock",
            0.11f,
            0.11f,
            0.025f,
            storageMaterial,
            new Vector3f(0f, 2.72f, 0.04f)
        ));
        return screenNode;
    }

    private Material resolveSeatMaterial(RoomSeatVisualState state) {
        if (state == null) {
            return unavailableSeatMaterial;
        }

        return switch (state) {
            case AVAILABLE -> availableSeatMaterial;
            case RESERVED -> reservedSeatMaterial;
            case MAINTENANCE -> maintenanceSeatMaterial;
            case UNAVAILABLE -> unavailableSeatMaterial;
        };
    }

    private void attachCeilingLights(LayoutMetrics metrics) {
        float[] xPositions = {-metrics.roomWidth() * 0.18f, metrics.roomWidth() * 0.18f};
        float[] zPositions = {-metrics.roomDepth() * 0.22f, metrics.roomDepth() * 0.05f, metrics.roomDepth() * 0.32f};
        ceilingLightPanels = new Node("ceiling-light-panels");
        sceneRoot.attachChild(ceilingLightPanels);

        for (int row = 0; row < zPositions.length; row++) {
            for (int column = 0; column < xPositions.length; column++) {
                float x = xPositions[column];
                float z = zPositions[row];
                ceilingLightPanels.attachChild(createSimpleBox(
                    "ceiling-light-" + row + "-" + column,
                    0.5f,
                    0.02f,
                    0.82f,
                    lightPanelMaterial,
                    new Vector3f(x, WALL_HEIGHT - 0.12f, z)
                ));

                PointLight pointLight = new PointLight();
                pointLight.setColor(new ColorRGBA(1f, 0.96f, 0.9f, 1f).mult(0.68f));
                pointLight.setRadius(Math.max(7f, metrics.roomWidth() * 0.9f));
                pointLight.setPosition(new Vector3f(x, WALL_HEIGHT - 0.32f, z));
                sceneRoot.addLight(pointLight);
            }
        }
    }

    private void attachLights() {
        AmbientLight ambientLight = new AmbientLight();
        ambientLight.setColor(new ColorRGBA(1f, 0.995f, 0.985f, 1f).mult(0.46f));
        sceneRoot.addLight(ambientLight);

        primaryShadowLight = new DirectionalLight();
        primaryShadowLight.setDirection(new Vector3f(-0.58f, -0.78f, -0.24f).normalizeLocal());
        primaryShadowLight.setColor(new ColorRGBA(1f, 0.98f, 0.95f, 1f).mult(0.62f));
        sceneRoot.addLight(primaryShadowLight);
        if (shadowRenderer != null) {
            shadowRenderer.setLight(primaryShadowLight);
        }

        DirectionalLight fillLight = new DirectionalLight();
        fillLight.setDirection(new Vector3f(0.42f, -0.9f, 0.18f).normalizeLocal());
        fillLight.setColor(new ColorRGBA(0.96f, 0.98f, 1f, 1f).mult(0.16f));
        sceneRoot.addLight(fillLight);
    }

    private void positionCamera(LayoutMetrics metrics) {
        float maxDimension = Math.max(metrics.roomWidth(), metrics.roomDepth());
        activeLayoutMetrics = metrics;
        updateCameraLens();
        applyCameraPreset(CameraPreset.ENTRANCE, false);
        cam.setFrustumFar(Math.max(250f, maxDimension * 12f));
        updateCameraLens();
        flyCam.setMoveSpeed(Math.max(10f, maxDimension * 1.2f));
    }

    private CameraView buildCameraView(LayoutMetrics metrics, CameraPreset preset) {
        float maxDimension = Math.max(metrics.roomWidth(), metrics.roomDepth());
        float halfRoomWidth = metrics.roomWidth() / 2f;
        float halfRoomDepth = metrics.roomDepth() / 2f;
        float screenCenterZ = Math.min(halfRoomDepth - 0.82f, metrics.frontSeatZ() + 2.43f);
        // Anchor the teacher view to the board side so it always stays at the front of the room
        // and looks back over nearly the whole classroom.
        float teacherCameraZ = Math.min(
            screenCenterZ - Math.max(0.06f, metrics.seatSpacingZ() * 0.02f),
            halfRoomDepth - 0.18f
        );
        float teacherFocusZ = -Math.max(2.05f, metrics.layoutDepth() * 0.7f);

        return switch (preset) {
            case ENTRANCE -> new CameraView(
                new Vector3f(
                    Math.min(-halfRoomWidth - 2.4f, -metrics.roomWidth() * 0.7f),
                    Math.max(7.05f, maxDimension * 0.66f),
                    Math.max(halfRoomDepth + 2.35f, metrics.roomDepth() * 0.72f)
                ),
                new Vector3f(0f, 1.02f, -metrics.roomDepth() * 0.04f),
                SHOWCASE_CAMERA_FOV
            );
            case BACK -> new CameraView(
                new Vector3f(
                    -metrics.roomWidth() * 0.14f,
                    Math.min(3.4f, Math.max(2.7f, maxDimension * 0.27f)),
                    Math.max(-halfRoomDepth - 0.2f, -metrics.roomDepth() * 0.66f)
                ),
                new Vector3f(0f, 1.45f, screenCenterZ - 0.02f),
                SHOWCASE_CAMERA_FOV + 2f
            );
            case TEACHER -> new CameraView(
                new Vector3f(
                    0f,
                    Math.min(2.08f, Math.max(1.92f, maxDimension * 0.17f)),
                    teacherCameraZ
                ),
                new Vector3f(
                    0f,
                    1.18f,
                    teacherFocusZ
                ),
                TEACHER_CAMERA_FOV
            );
            case TOP -> new CameraView(
                new Vector3f(
                    0f,
                    Math.max(6.4f, maxDimension * 0.8f),
                    0f
                ),
                new Vector3f(0f, 0.55f, 0f),
                SHOWCASE_CAMERA_FOV
            );
        };
    }

    private void applyCameraPreset(CameraPreset preset, boolean animate) {
        if (activeLayoutMetrics == null || preset == null) {
            return;
        }

        CameraView targetView = buildCameraView(activeLayoutMetrics, preset);
        activeCameraPreset = preset;
        updateCeilingVisibilityForPreset(preset);
        updateCameraPresetButtonStyles();

        if (animate) {
            startCameraTransition(targetView);
            return;
        }

        activeCameraTransition = null;
        activeCameraTransitionElapsed = 0f;
        setCameraView(targetView);
    }

    private void startCameraTransition(CameraView targetView) {
        Vector3f transitionStartLocation = cam.getLocation().clone();
        Vector3f transitionStartTarget = estimateCurrentLookTarget();
        activeCameraTransition = new CameraTransition(
            transitionStartLocation,
            targetView.location().clone(),
            transitionStartTarget,
            targetView.target().clone(),
            activeCameraFov,
            targetView.fovDegrees()
        );
        activeCameraTransitionElapsed = 0f;
    }

    private void updateCameraTransition(float tpf) {
        if (activeCameraTransition == null) {
            return;
        }

        activeCameraTransitionElapsed += tpf;
        float normalizedProgress = Math.min(1f, activeCameraTransitionElapsed / CAMERA_TRANSITION_DURATION_SECONDS);
        float easedProgress = smoothStep(normalizedProgress);

        setCameraView(new CameraView(
            interpolate(activeCameraTransition.startLocation(), activeCameraTransition.endLocation(), easedProgress),
            interpolate(activeCameraTransition.startTarget(), activeCameraTransition.endTarget(), easedProgress),
            interpolate(activeCameraTransition.startFov(), activeCameraTransition.endFov(), easedProgress)
        ));

        if (normalizedProgress >= 1f) {
            activeCameraTransition = null;
            activeCameraTransitionElapsed = 0f;
        }
    }

    private void setCameraView(CameraView view) {
        activeCameraFov = view.fovDegrees();
        updateCameraLens();
        cam.setLocation(view.location().clone());
        cam.lookAt(view.target(), Vector3f.UNIT_Y);
    }

    private void updateCeilingVisibilityForPreset(CameraPreset preset) {
        if (ceilingCutaway == null) {
            return;
        }

        Spatial.CullHint ceilingCullHint =
            preset == CameraPreset.TOP || preset == CameraPreset.ENTRANCE
                ? Spatial.CullHint.Always
                : Spatial.CullHint.Inherit;

        ceilingCutaway.setCullHint(ceilingCullHint);
        if (ceilingLightPanels != null) {
            ceilingLightPanels.setCullHint(ceilingCullHint);
        }
    }

    private Vector3f estimateCurrentLookTarget() {
        if (activeLayoutMetrics == null) {
            return cam.getLocation().add(cam.getDirection().mult(8f));
        }

        float focusDistance = Math.max(activeLayoutMetrics.roomWidth(), activeLayoutMetrics.roomDepth()) * 0.78f;
        return cam.getLocation().add(cam.getDirection().mult(focusDistance));
    }

    private float smoothStep(float progress) {
        return progress * progress * (3f - (2f * progress));
    }

    private Vector3f interpolate(Vector3f start, Vector3f end, float progress) {
        return new Vector3f(
            start.x + ((end.x - start.x) * progress),
            start.y + ((end.y - start.y) * progress),
            start.z + ((end.z - start.z) * progress)
        );
    }

    private float interpolate(float start, float end, float progress) {
        return start + ((end - start) * progress);
    }

    private void updateHoveredSeat() {
        if (sceneRoot == null) {
            return;
        }

        if (primarySelectionArmed || findCameraPresetAtCursor(inputManager.getCursorPosition()) != null) {
            setHoveredSeat(null);
            return;
        }

        Node hoveredSeat = findSeatNodeAtCursor();
        setHoveredSeat(hoveredSeat);
        updateHoverTextPosition();
    }

    private LayoutMetrics buildLayoutMetrics(Room3DPreviewData previewData) {
        String disposition = normalize(previewData.disposition());

        float seatSpacingX = switch (disposition) {
            case "conference" -> 1.45f;
            case "informatique" -> 1.5f;
            case "u", "reunion" -> 1.65f;
            default -> 1.3f;
        };
        float seatSpacingZ = switch (disposition) {
            case "conference" -> 1.55f;
            case "u" -> 1.75f;
            case "reunion" -> 1.45f;
            default -> 1.35f;
        };

        int minRow = previewData.minRow();
        int minColumn = previewData.minColumn();
        float layoutWidth = Math.max(2.6f, Math.max(0, previewData.columnSpan() - 1) * seatSpacingX);
        float layoutDepth = Math.max(2.6f, Math.max(0, previewData.rowSpan() - 1) * seatSpacingZ);
        float roomWidth = layoutWidth + 5.2f;
        float roomDepth = layoutDepth + 6.6f;
        float frontSeatZ = layoutDepth / 2f;

        return new LayoutMetrics(seatSpacingX, seatSpacingZ, layoutWidth, layoutDepth, roomWidth, roomDepth, frontSeatZ, minRow, minColumn);
    }

    private String buildDefaultSummary() {
        return previewData.summaryLine()
            + " | Disposition: "
            + previewData.disposition()
            + " | Etat: "
            + previewData.roomStatus()
            + " | Acces PMR: "
            + (previewData.accessible() ? "oui" : "non");
    }

    private String buildDefaultSelectionText() {
        if (previewData.supportsSeatSelection()) {
            return "Selection: aucune | Choisissez une place libre pour preparer la reservation 3D.";
        }
        return "Selection: aucune | Cliquez sur une place pour l'epingler dans le panneau d'information.";
    }

    private String buildLegendText() {
        return "Legende places | vert "
            + countSeats(RoomSeatVisualState.AVAILABLE)
            + " disponibles | orange "
            + countSeats(RoomSeatVisualState.RESERVED)
            + " reservees | jaune "
            + countSeats(RoomSeatVisualState.MAINTENANCE)
            + " maintenance | gris "
            + countSeats(RoomSeatVisualState.UNAVAILABLE)
            + " indisponibles";
    }

    private String buildInteractionHintText() {
        if (previewData.supportsSeatSelection()) {
            return "Survol: apercu | Clic: choisir une place libre | Boutons camera: vues rapides | Glisser: camera | ZQSD/WASD: deplacement";
        }
        return "Survol: apercu | Clic: selection d'information | Boutons camera: vues rapides | Glisser: camera | ZQSD/WASD: deplacement";
    }

    private int countSeats(RoomSeatVisualState state) {
        int count = 0;
        for (Room3DPreviewData.SeatPreview seat : previewData.seats()) {
            if (seat.state() == state) {
                count++;
            }
        }
        return count;
    }

    private Geometry createSimpleBox(String name, float halfWidth, float halfHeight, float halfDepth,
                                     Material material, Vector3f position) {
        Geometry geometry = new Geometry(name, new Box(halfWidth, halfHeight, halfDepth));
        geometry.setMaterial(material);
        geometry.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        geometry.setLocalTranslation(position);
        return geometry;
    }

    private void updateCameraPresetHover() {
        if (inputManager == null) {
            return;
        }

        CameraPreset newHoveredPreset = findCameraPresetAtCursor(inputManager.getCursorPosition());
        if (newHoveredPreset == hoveredCameraPreset) {
            return;
        }

        hoveredCameraPreset = newHoveredPreset;
        updateCameraPresetButtonStyles();
    }

    private void handlePrimarySeatInteraction(boolean isPressed) {
        if (isPressed) {
            if (activeCameraTransition != null && findCameraPresetAtCursor(inputManager.getCursorPosition()) == null) {
                activeCameraTransition = null;
                activeCameraTransitionElapsed = 0f;
            }
            primarySelectionArmed = true;
            primaryClickStart = inputManager.getCursorPosition().clone();
            return;
        }

        Vector2f clickStart = primaryClickStart;
        primarySelectionArmed = false;
        primaryClickStart = null;
        if (clickStart == null) {
            return;
        }

        float dragDistance = clickStart.distance(inputManager.getCursorPosition());
        if (dragDistance > CLICK_DRAG_THRESHOLD) {
            return;
        }

        CameraPreset clickedPreset = findCameraPresetAtCursor(inputManager.getCursorPosition());
        if (clickedPreset != null) {
            applyCameraPreset(clickedPreset, true);
            return;
        }

        selectSeat(findSeatNodeAtCursor());
    }

    private CameraPreset findCameraPresetAtCursor(Vector2f cursorPosition) {
        if (cursorPosition == null || cameraPresetButtons.isEmpty()) {
            return null;
        }

        for (CameraPreset preset : CameraPreset.values()) {
            CameraPresetButton button = cameraPresetButtons.get(preset);
            if (button == null) {
                continue;
            }

            Vector3f buttonPosition = button.node().getLocalTranslation();
            float x = buttonPosition.x;
            float y = buttonPosition.y;
            if (cursorPosition.x >= x
                && cursorPosition.x <= x + button.width()
                && cursorPosition.y >= y
                && cursorPosition.y <= y + button.height()) {
                return preset;
            }
        }

        return null;
    }

    private Node findSeatNodeAtCursor() {
        if (sceneRoot == null) {
            return null;
        }

        CollisionResults collisions = new CollisionResults();
        Vector2f cursorPosition = inputManager.getCursorPosition().clone();
        Vector3f rayOrigin = cam.getWorldCoordinates(cursorPosition, 0f);
        Vector3f rayDirection = cam.getWorldCoordinates(cursorPosition, 1f).subtractLocal(rayOrigin).normalizeLocal();
        sceneRoot.collideWith(new Ray(rayOrigin, rayDirection), collisions);

        for (int index = 0; index < collisions.size(); index++) {
            Node seatNode = resolveSeatNode(collisions.getCollision(index).getGeometry());
            if (seatNode != null) {
                return seatNode;
            }
        }

        return null;
    }

    private Node resolveSeatNode(Spatial spatial) {
        Spatial current = spatial;
        while (current != null) {
            if (current instanceof Node node && seatVisuals.containsKey(node)) {
                return node;
            }
            current = current.getParent();
        }
        return null;
    }

    private void setHoveredSeat(Node seatNode) {
        if (hoveredSeatNode == seatNode) {
            updateHoverText(seatNode);
            return;
        }

        Node previousHoveredSeat = hoveredSeatNode;
        hoveredSeatNode = seatNode;
        refreshSeatVisual(previousHoveredSeat);
        refreshSeatVisual(hoveredSeatNode);
        updateHoverText(hoveredSeatNode);
    }

    private void selectSeat(Node seatNode) {
        if (seatNode != null && previewData.supportsSeatSelection() && !isSeatSelectable(seatNode)) {
            Room3DPreviewData.SeatPreview seat = resolveSeatPreview(seatNode);
            Node previousSelectedSeat = selectedSeatNode;
            selectedSeatNode = null;
            refreshSeatVisual(previousSelectedSeat);
            refreshSeatVisual(hoveredSeatNode);
            selectedSeatIdSnapshot = null;
            selectedSeatLabelSnapshot = null;
            selectionText.setText(buildUnavailableSelectionText(seat));
            selectionText.setColor(resolveSeatHudColor(seat == null ? RoomSeatVisualState.UNAVAILABLE : seat.state()));
            updateHudPositions();
            return;
        }

        Node previousSelectedSeat = selectedSeatNode;
        selectedSeatNode = seatNode != null && seatNode == selectedSeatNode ? null : seatNode;
        refreshSeatVisual(previousSelectedSeat);
        refreshSeatVisual(selectedSeatNode);
        refreshSeatVisual(hoveredSeatNode);
        updateSelectionText();
    }

    private void refreshSeatVisual(Node seatNode) {
        if (seatNode == null) {
            return;
        }

        SeatVisual seatVisual = seatVisuals.get(seatNode);
        if (seatVisual == null) {
            return;
        }

        boolean selected = seatNode == selectedSeatNode;
        boolean hovered = seatNode == hoveredSeatNode;

        if (!selected && !hovered) {
            seatNode.setLocalScale(1f);
            seatVisual.indicator().setCullHint(Spatial.CullHint.Always);
            return;
        }

        seatVisual.indicator().setCullHint(Spatial.CullHint.Inherit);
        if (selected) {
            seatNode.setLocalScale(SELECTED_SEAT_SCALE);
            seatVisual.indicator().setMaterial(selectedIndicatorMaterial);
            return;
        }

        seatNode.setLocalScale(HOVERED_SEAT_SCALE);
        seatVisual.indicator().setMaterial(hoverIndicatorMaterial);
    }

    private void updateSelectionText() {
        if (selectionText == null) {
            return;
        }

        if (selectedSeatNode == null) {
            selectionText.setText(buildDefaultSelectionText());
            selectionText.setColor(createHudNeutralColor());
            selectedSeatIdSnapshot = null;
            selectedSeatLabelSnapshot = null;
            updateHudPositions();
            return;
        }

        SeatVisual seatVisual = seatVisuals.get(selectedSeatNode);
        if (seatVisual == null) {
            selectionText.setText(buildDefaultSelectionText());
            selectionText.setColor(createHudNeutralColor());
            selectedSeatIdSnapshot = null;
            selectedSeatLabelSnapshot = null;
            updateHudPositions();
            return;
        }

        Room3DPreviewData.SeatPreview seat = seatVisual.seat();
        if (previewData.supportsSeatSelection() && seat.selectable()) {
            selectionText.setText("Choix 3D: " + seat.label() + " | Etat: " + seat.state().displayLabel());
        } else {
            selectionText.setText("Selection: " + seat.label() + " | Etat: " + seat.state().displayLabel());
        }
        selectionText.setColor(resolveSeatHudColor(seat.state()));
        updateSelectionSnapshot(seat);
        updateHudPositions();
    }

    private void updateHoverText(Node seatNode) {
        if (hoverText == null) {
            return;
        }

        if (seatNode == null) {
            hideHoverText();
            return;
        }

        SeatVisual seatVisual = seatVisuals.get(seatNode);
        if (seatVisual == null) {
            hideHoverText();
            return;
        }

        Room3DPreviewData.SeatPreview seat = seatVisual.seat();
        hoverText.setText(seat.label() + "\nEtat: " + seat.state().displayLabel());
        hoverText.setColor(resolveSeatHudColor(seat.state()));
        hoverText.setCullHint(Spatial.CullHint.Inherit);
        updateHoverTextPosition();
    }

    private void hideHoverText() {
        if (hoverText == null) {
            return;
        }

        hoverText.setText("");
        hoverText.setCullHint(Spatial.CullHint.Always);
    }

    private void updateHoverTextPosition() {
        if (hoverText == null || hoverText.getCullHint() == Spatial.CullHint.Always || inputManager == null) {
            return;
        }

        Vector2f cursorPosition = inputManager.getCursorPosition();
        float tooltipWidth = hoverText.getLineWidth();
        float tooltipHeight = estimateTextBlockHeight(hoverText.getText(), hoverText.getLineHeight());
        float tooltipX = Math.max(18f, Math.min(cam.getWidth() - tooltipWidth - 18f, cursorPosition.x + 18f));
        float tooltipY = cursorPosition.y + tooltipHeight + 14f;
        if (tooltipY > cam.getHeight() - 18f) {
            tooltipY = cursorPosition.y - 12f;
        }
        tooltipY = Math.max(tooltipHeight + 18f, Math.min(cam.getHeight() - 18f, tooltipY));
        hoverText.setLocalTranslation(tooltipX, tooltipY, 0f);
    }

    private float estimateTextBlockHeight(String text, float lineHeight) {
        if (text == null || text.isEmpty()) {
            return lineHeight;
        }

        int lineCount = 1;
        for (int index = 0; index < text.length(); index++) {
            if (text.charAt(index) == '\n') {
                lineCount++;
            }
        }
        return lineCount * lineHeight;
    }

    private ColorRGBA resolveSeatHudColor(RoomSeatVisualState state) {
        if (state == null) {
            return new ColorRGBA(0.5f, 0.54f, 0.62f, 1f);
        }

        return switch (state) {
            case AVAILABLE -> new ColorRGBA(0.16f, 0.58f, 0.34f, 1f);
            case RESERVED -> new ColorRGBA(0.86f, 0.43f, 0.18f, 1f);
            case MAINTENANCE -> new ColorRGBA(0.78f, 0.63f, 0.16f, 1f);
            case UNAVAILABLE -> new ColorRGBA(0.47f, 0.5f, 0.57f, 1f);
        };
    }

    private ColorRGBA createHudNeutralColor() {
        return new ColorRGBA(0.12f, 0.18f, 0.28f, 1f);
    }

    private Room3DPreviewData.SeatPreview resolveSeatPreview(Node seatNode) {
        SeatVisual seatVisual = seatVisuals.get(seatNode);
        return seatVisual == null ? null : seatVisual.seat();
    }

    private boolean isSeatSelectable(Node seatNode) {
        Room3DPreviewData.SeatPreview seat = resolveSeatPreview(seatNode);
        return seat != null && seat.selectable();
    }

    private String buildUnavailableSelectionText(Room3DPreviewData.SeatPreview seat) {
        if (seat == null) {
            return "Selection impossible | Cette place ne peut pas etre reservee dans la vue 3D actuelle.";
        }
        return seat.label() + " | Etat: " + seat.state().displayLabel() + " | Selection impossible";
    }

    private void updateSelectionSnapshot(Room3DPreviewData.SeatPreview seat) {
        if (seat == null || !previewData.supportsSeatSelection() || !seat.selectable() || !seat.hasPersistentId()) {
            selectedSeatIdSnapshot = null;
            selectedSeatLabelSnapshot = null;
            return;
        }

        selectedSeatIdSnapshot = seat.seatId();
        selectedSeatLabelSnapshot = seat.label();
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }

    private enum CameraPreset {
        ENTRANCE("Vue entree", 0, 0),
        BACK("Vue fond de salle", 0, 1),
        TEACHER("Vue enseignant", 1, 0),
        TOP("Vue dessus", 1, 1);

        private final String label;
        private final int row;
        private final int column;

        CameraPreset(String label, int row, int column) {
            this.label = label;
            this.row = row;
            this.column = column;
        }

        private String label() {
            return label;
        }

        private int row() {
            return row;
        }

        private int column() {
            return column;
        }
    }

    private record CameraView(Vector3f location, Vector3f target, float fovDegrees) {
    }

    private record CameraTransition(
        Vector3f startLocation,
        Vector3f endLocation,
        Vector3f startTarget,
        Vector3f endTarget,
        float startFov,
        float endFov
    ) {
    }

    private record CameraPresetButton(
        CameraPreset preset,
        Node node,
        Geometry background,
        BitmapText label,
        float width,
        float height
    ) {
    }

    private record SeatVisual(Room3DPreviewData.SeatPreview seat, Geometry indicator) {
    }

    private record LayoutMetrics(
        float seatSpacingX,
        float seatSpacingZ,
        float layoutWidth,
        float layoutDepth,
        float roomWidth,
        float roomDepth,
        float frontSeatZ,
        int minRow,
        int minColumn
    ) {
    }
}
