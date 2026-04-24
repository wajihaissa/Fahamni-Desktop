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
    private static final float CAMERA_TRANSITION_DURATION_SECONDS = 0.75f;
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
    private static final float QUARTER_TURN_Y = (float) (Math.PI / 2d);
    private static final float HALF_TURN_Y = (float) Math.PI;
    private static final float SHOWCASE_CAMERA_FOV = 42f;
    private static final float TEACHER_CAMERA_FOV = 88f;
    private static final String FLOOR_TEXTURE_PATH = "com/fahamni/room3d/textures/floor-concrete-light.png";
    private static final String WALL_TEXTURE_PATH = "com/fahamni/room3d/textures/wall-plaster-soft.png";
    private static final String WOOD_TEXTURE_PATH = "com/fahamni/room3d/textures/desk-oak-light.png";

    private Room3DPreviewData previewData;
    private Node sceneRoot;
    private Geometry ceilingCutaway;
    private Geometry frontWall;
    private Geometry frontBorder;
    private Geometry frontBorderTrim;
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
        viewPort.setBackgroundColor(new ColorRGBA(0.88f, 0.91f, 0.95f, 1f));

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

    private void setSelectedSeatSnapshot(Integer seatId, String seatLabel) {
        selectedSeatIdSnapshot = seatId;
        selectedSeatLabelSnapshot = seatId == null || seatLabel == null || seatLabel.isBlank()
            ? null
            : seatLabel;
        Room3DViewerLauncher.updateSelectedSeatSnapshot(selectedSeatIdSnapshot, selectedSeatLabelSnapshot);
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
        setSelectedSeatSnapshot(null, null);
        activeLayoutMetrics = null;
        activeCameraPreset = CameraPreset.ENTRANCE;
        hoveredCameraPreset = null;
        activeCameraTransition = null;
        activeCameraTransitionElapsed = 0f;
        activeCameraFov = SHOWCASE_CAMERA_FOV;
        ceilingCutaway = null;
        frontWall = null;
        frontBorder = null;
        frontBorderTrim = null;
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

        titleText.setText("");
        summaryText.setText(buildDefaultSummary());
        selectionText.setText(buildDefaultSelectionText());
        selectionText.setColor(createHudNeutralColor());
        legendText.setText(buildLegendText());
        interactionHintText.setText(buildInteractionHintText());
        updateBitmapTextVisibility(titleText);
        updateBitmapTextVisibility(summaryText);
        updateBitmapTextVisibility(selectionText);
        updateBitmapTextVisibility(legendText);
        updateBitmapTextVisibility(interactionHintText);
        hideHoverText();
        updateHudPositions();
    }

    private void initialiseHud() {
        titleText = new BitmapText(hudFont);
        titleText.setSize(hudFont.getCharSet().getRenderedSize() * 1.45f);
        titleText.setColor(ColorRGBA.Black);
        titleText.setCullHint(Spatial.CullHint.Always);

        summaryText = new BitmapText(hudFont);
        summaryText.setSize(hudFont.getCharSet().getRenderedSize());
        summaryText.setColor(new ColorRGBA(0.12f, 0.18f, 0.28f, 1f));
        summaryText.setCullHint(Spatial.CullHint.Always);

        selectionText = new BitmapText(hudFont);
        selectionText.setSize(hudFont.getCharSet().getRenderedSize() * 0.98f);
        selectionText.setColor(createHudNeutralColor());
        selectionText.setCullHint(Spatial.CullHint.Always);

        legendText = new BitmapText(hudFont);
        legendText.setSize(hudFont.getCharSet().getRenderedSize() * 0.86f);
        legendText.setColor(new ColorRGBA(0.23f, 0.29f, 0.38f, 1f));
        legendText.setCullHint(Spatial.CullHint.Always);

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
        if (selectionText == null || interactionHintText == null || hoverText == null) {
            return;
        }

        if (selectionText.getCullHint() != Spatial.CullHint.Always) {
            selectionText.setLocalTranslation(18f, cam.getHeight() - 22f, 0f);
        }
        interactionHintText.setLocalTranslation(18f, 24f + interactionHintText.getLineHeight(), 0f);
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
        floorMaterial = createTexturedLitMaterial(FLOOR_TEXTURE_PATH, new ColorRGBA(0.89f, 0.89f, 0.88f, 1f), 0.46f, 0.06f, 7f);
        wallMaterial = createTexturedLitMaterial(WALL_TEXTURE_PATH, new ColorRGBA(0.96f, 0.95f, 0.92f, 1f), 0.5f, 0.02f, 1.8f);
        ceilingMaterial = createTexturedLitMaterial(WALL_TEXTURE_PATH, new ColorRGBA(0.94f, 0.95f, 0.96f, 1f), 0.42f, 0.01f, 1.2f);
        deskMaterial = createTexturedLitMaterial(WOOD_TEXTURE_PATH, new ColorRGBA(0.94f, 0.88f, 0.78f, 1f), 0.38f, 0.18f, 18f);
        stageMaterial = createTexturedLitMaterial(FLOOR_TEXTURE_PATH, new ColorRGBA(0.87f, 0.87f, 0.86f, 1f), 0.4f, 0.03f, 4.5f);
        trimMaterial = createLitMaterial(new ColorRGBA(0.82f, 0.84f, 0.87f, 1f), 0.5f, 0.1f, 8f);
        metalMaterial = createLitMaterial(new ColorRGBA(0.29f, 0.31f, 0.35f, 1f), 0.26f, 0.36f, 22f);
        glassMaterial = createLitMaterial(new ColorRGBA(0.78f, 0.84f, 0.9f, 1f), 0.22f, 0.24f, 14f);
        lightPanelMaterial = createFlatMaterial(new ColorRGBA(0.97f, 0.97f, 0.95f, 1f));
        screenMaterial = createFlatMaterial(new ColorRGBA(0.15f, 0.16f, 0.18f, 1f));
        boardMaterial = createLitMaterial(new ColorRGBA(0.86f, 0.89f, 0.93f, 1f), 0.38f, 0.05f, 4f);
        storageMaterial = createTexturedLitMaterial(WALL_TEXTURE_PATH, new ColorRGBA(0.88f, 0.89f, 0.9f, 1f), 0.42f, 0.04f, 4.5f);
        accentMaterial = createFlatMaterial(new ColorRGBA(0.72f, 0.85f, 0.92f, 1f));
        availableSeatMaterial = createLitMaterial(new ColorRGBA(0.24f, 0.34f, 0.56f, 1f));
        reservedSeatMaterial = createLitMaterial(new ColorRGBA(0.72f, 0.42f, 0.31f, 1f));
        maintenanceSeatMaterial = createLitMaterial(new ColorRGBA(0.69f, 0.58f, 0.24f, 1f));
        unavailableSeatMaterial = createLitMaterial(new ColorRGBA(0.5f, 0.54f, 0.6f, 1f));
        hoverIndicatorMaterial = createFlatMaterial(new ColorRGBA(0.78f, 0.69f, 0.55f, 0.88f));
        selectedIndicatorMaterial = createFlatMaterial(new ColorRGBA(0.88f, 0.68f, 0.36f, 0.96f));
    }

    private Material createLitMaterial(ColorRGBA baseColor) {
        return createLitMaterial(baseColor, 0.5f, 0.12f, 12f);
    }

    private Material createLitMaterial(ColorRGBA baseColor, float ambientFactor, float specularStrength, float shininess) {
        Material material = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        material.setBoolean("UseMaterialColors", true);
        material.setColor("Diffuse", baseColor);
        material.setColor("Ambient", baseColor.mult(ambientFactor));
        material.setColor("Specular", ColorRGBA.White.mult(specularStrength));
        material.setFloat("Shininess", shininess);
        return material;
    }

    private Material createTexturedLitMaterial(String texturePath, ColorRGBA baseColor, float ambientFactor, float shininess) {
        return createTexturedLitMaterial(texturePath, baseColor, ambientFactor, 0.12f, shininess);
    }

    private Material createTexturedLitMaterial(
        String texturePath,
        ColorRGBA baseColor,
        float ambientFactor,
        float specularStrength,
        float shininess
    ) {
        Material material = createLitMaterial(baseColor, ambientFactor, specularStrength, shininess);
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
        shadowRenderer.setShadowIntensity(0.42f);
        shadowRenderer.setEdgeFilteringMode(EdgeFilteringMode.PCF8);
        shadowRenderer.setShadowCompareMode(CompareMode.Hardware);
        shadowRenderer.setEnabledStabilization(true);
        shadowRenderer.setShadowZExtend(48f);
        shadowRenderer.setShadowZFadeLength(18f);
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
        frontWall = createWall(
            "front-wall",
            metrics.roomWidth(),
            WALL_HEIGHT,
            wallThickness,
            new Vector3f(0f, WALL_HEIGHT / 2f - FLOOR_THICKNESS, halfRoomDepth)
        );
        sceneRoot.attachChild(frontWall);
        frontBorder = createWall(
            "front-border",
            metrics.roomWidth(),
            FRONT_BORDER_HEIGHT,
            wallThickness,
            new Vector3f(0f, FRONT_BORDER_HEIGHT / 2f - FLOOR_THICKNESS, halfRoomDepth)
        );
        sceneRoot.attachChild(frontBorder);
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

        frontBorderTrim = createSimpleBox(
            "front-border-trim",
            metrics.roomWidth(),
            0.03f,
            0.05f,
            trimMaterial,
            new Vector3f(0f, FRONT_BORDER_HEIGHT + frameOffset, halfRoomDepth)
        );
        sceneRoot.attachChild(frontBorderTrim);
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
        String disposition = normalize(previewData.disposition());
        boolean amphitheatreLayout = isAmphitheatreConferenceLayout();
        ConferenceAuditoriumLayout conferenceAuditoriumLayout = isTieredAuditoriumLayout()
            ? buildConferenceAuditoriumLayout(metrics)
            : null;
        float frontZoneZ = conferenceAuditoriumLayout == null
            ? metrics.frontSeatZ() + 1.55f
            : conferenceAuditoriumLayout.stageZoneZ();
        float stageHalfWidth = conferenceAuditoriumLayout == null
            ? Math.max(2.8f, metrics.layoutWidth() + 1.6f)
            : amphitheatreLayout
                ? Math.max(5.2f, metrics.roomWidth() - 0.95f)
                : Math.max(4.4f, metrics.roomWidth() - 1.4f);
        float stageHalfDepth = conferenceAuditoriumLayout == null
            ? 1.25f
            : amphitheatreLayout ? 1.76f : 1.38f;
        sceneRoot.attachChild(createSimpleBox(
            "teaching-platform",
            stageHalfWidth,
            0.05f,
            stageHalfDepth,
            stageMaterial,
            new Vector3f(0f, 0f, frontZoneZ + 0.28f)
        ));

        Node presentationScreen = createPresentationScreen();
        if (amphitheatreLayout) {
            presentationScreen.setLocalScale(1.16f, 1.12f, 1f);
            presentationScreen.setLocalTranslation(0f, 0.08f, frontZoneZ + 1.02f);
        } else {
            presentationScreen.setLocalTranslation(0f, 0f, frontZoneZ + 0.88f);
        }
        sceneRoot.attachChild(presentationScreen);

        if (isConferenceUShapeLayout()) {
            attachConferenceUTable(metrics);
        } else if (conferenceAuditoriumLayout != null) {
            Node lectern = createConferenceLecternNode();
            lectern.setLocalTranslation(0f, 0f, frontZoneZ - (amphitheatreLayout ? 0.18f : 0.06f));
            sceneRoot.attachChild(lectern);
        } else if ("reunion".equals(disposition)) {
            attachReunionTable(metrics);
        } else {
            Node desk = createTeacherDeskNode();
            desk.setLocalTranslation(-Math.max(1.45f, metrics.layoutWidth() * 0.38f), 0f, frontZoneZ - 0.12f);
            sceneRoot.attachChild(desk);

            Node storageShelf = createStorageShelfNode();
            storageShelf.setLocalTranslation(Math.max(1.65f, metrics.layoutWidth() * 0.44f), 0f, frontZoneZ + 0.16f);
            sceneRoot.attachChild(storageShelf);
        }
    }

    private void attachReunionTable(LayoutMetrics metrics) {
        ReunionTableLayout reunionTableLayout = buildReunionTableLayout(metrics);
        Node centralTable = createTableNode(
            "central-table",
            reunionTableLayout.tableWidth(),
            reunionTableLayout.tableDepth(),
            0.76f,
            deskMaterial
        );
        centralTable.setLocalTranslation(0f, 0f, reunionTableLayout.tableZ());
        centralTable.attachChild(createSimpleBox(
            "conference-hub",
            0.18f,
            0.03f,
            0.18f,
            metalMaterial,
            new Vector3f(0f, 0.81f, 0f)
        ));
        sceneRoot.attachChild(centralTable);
        attachReunionMicrophones(metrics, reunionTableLayout);
    }

    private void attachConferenceUTable(LayoutMetrics metrics) {
        ConferenceUShapeLayout uShapeLayout = buildConferenceUShapeLayout(metrics);
        float tableHeight = 0.74f;
        Node leftWing = createTableNode(
            "conference-u-left-wing",
            uShapeLayout.sideTableWidth(),
            uShapeLayout.sideTableDepth(),
            tableHeight,
            deskMaterial
        );
        leftWing.setLocalTranslation(uShapeLayout.leftTableX(), 0f, uShapeLayout.sideTableCenterZ());
        sceneRoot.attachChild(leftWing);

        Node rightWing = createTableNode(
            "conference-u-right-wing",
            uShapeLayout.sideTableWidth(),
            uShapeLayout.sideTableDepth(),
            tableHeight,
            deskMaterial
        );
        rightWing.setLocalTranslation(uShapeLayout.rightTableX(), 0f, uShapeLayout.sideTableCenterZ());
        sceneRoot.attachChild(rightWing);

        Node backWing = createTableNode(
            "conference-u-back-wing",
            uShapeLayout.backTableWidth(),
            uShapeLayout.backTableDepth(),
            tableHeight,
            deskMaterial
        );
        backWing.setLocalTranslation(0f, 0f, uShapeLayout.backTableZ());
        backWing.attachChild(createSimpleBox(
            "conference-u-console",
            0.24f,
            0.02f,
            0.14f,
            trimMaterial,
            new Vector3f(0f, tableHeight + 0.04f, 0f)
        ));
        sceneRoot.attachChild(backWing);
        attachConferenceUMicrophones(metrics, uShapeLayout);
    }

    private void attachReunionMicrophones(LayoutMetrics metrics, ReunionTableLayout reunionTableLayout) {
        for (Room3DPreviewData.SeatPreview seat : previewData.seats()) {
            Node microphoneNode = createConferenceMicrophoneNode(
                "reunion-microphone-" + seat.number(),
                resolveReunionMicrophoneYaw(seat)
            );
            microphoneNode.setLocalTranslation(resolveReunionMicrophonePosition(seat, metrics, reunionTableLayout));
            sceneRoot.attachChild(microphoneNode);
        }
    }

    private void attachConferenceUMicrophones(LayoutMetrics metrics, ConferenceUShapeLayout uShapeLayout) {
        for (Room3DPreviewData.SeatPreview seat : previewData.seats()) {
            Node microphoneNode = createConferenceMicrophoneNode(
                "conference-u-microphone-" + seat.number(),
                resolveConferenceUMicrophoneYaw(seat)
            );
            microphoneNode.setLocalTranslation(resolveConferenceUMicrophonePosition(seat, metrics, uShapeLayout));
            sceneRoot.attachChild(microphoneNode);
        }
    }

    private void attachSeats(LayoutMetrics metrics) {
        String disposition = normalize(previewData.disposition());
        if (isTieredAuditoriumLayout()) {
            attachConferenceAuditoriumRisers(metrics);
        }

        for (Room3DPreviewData.SeatPreview seat : previewData.seats()) {
            Node seatNode = createSeatNode(seat, disposition);
            seatNode.setLocalTranslation(resolveSeatPosition(seat, metrics, disposition));
            orientSeatNode(seatNode, seat, disposition);

            sceneRoot.attachChild(seatNode);
        }
    }

    private Vector3f resolveSeatPosition(Room3DPreviewData.SeatPreview seat, LayoutMetrics metrics, String disposition) {
        if (isConferenceUShapeLayout()) {
            return resolveConferenceUSeatPosition(seat, metrics);
        }
        if (isTieredAuditoriumLayout()) {
            return resolveConferenceAuditoriumSeatPosition(seat, metrics);
        }
        if ("reunion".equals(disposition)) {
            return resolveReunionSeatPosition(seat, metrics);
        }

        float x = ((seat.column() - metrics.minColumn()) * metrics.seatSpacingX()) - (metrics.layoutWidth() / 2f);
        float z = metrics.frontSeatZ() - ((seat.row() - metrics.minRow()) * metrics.seatSpacingZ());
        return new Vector3f(x, 0f, z);
    }

    private Vector3f resolveReunionSeatPosition(Room3DPreviewData.SeatPreview seat, LayoutMetrics metrics) {
        ReunionTableLayout reunionTableLayout = buildReunionTableLayout(metrics);
        ReunionSeatSlot seatSlot = resolveReunionSeatSlot(seat);

        return switch (seatSlot.side()) {
            case FRONT -> new Vector3f(
                interpolate(reunionTableLayout.frontSeatMinX(), reunionTableLayout.frontSeatMaxX(), seatSlot.factor()),
                0f,
                reunionTableLayout.frontSeatZ()
            );
            case BACK -> new Vector3f(
                interpolate(reunionTableLayout.backSeatMinX(), reunionTableLayout.backSeatMaxX(), seatSlot.factor()),
                0f,
                reunionTableLayout.backSeatZ()
            );
            case LEFT -> new Vector3f(
                reunionTableLayout.leftSeatX(),
                0f,
                interpolate(reunionTableLayout.sideSeatFrontZ(), reunionTableLayout.sideSeatBackZ(), seatSlot.factor())
            );
            case RIGHT -> new Vector3f(
                reunionTableLayout.rightSeatX(),
                0f,
                interpolate(reunionTableLayout.sideSeatFrontZ(), reunionTableLayout.sideSeatBackZ(), seatSlot.factor())
            );
        };
    }

    private Vector3f resolveConferenceAuditoriumSeatPosition(Room3DPreviewData.SeatPreview seat, LayoutMetrics metrics) {
        ConferenceAuditoriumLayout auditoriumLayout = buildConferenceAuditoriumLayout(metrics);
        int rowIndex = Math.max(0, seat.row() - metrics.minRow());
        int columnIndex = Math.max(0, seat.column() - metrics.minColumn());
        float x = resolveConferenceAuditoriumSeatX(columnIndex, rowIndex, auditoriumLayout);
        float y = rowIndex * auditoriumLayout.rowRise();
        float z = auditoriumLayout.frontSeatZ() - (rowIndex * auditoriumLayout.rowPitch()) + 0.12f;
        return new Vector3f(x, y, z);
    }

    private void attachConferenceAuditoriumRisers(LayoutMetrics metrics) {
        ConferenceAuditoriumLayout auditoriumLayout = buildConferenceAuditoriumLayout(metrics);
        for (int rowIndex = 1; rowIndex < auditoriumLayout.rowCount(); rowIndex++) {
            float rowTopY = rowIndex * auditoriumLayout.rowRise();
            float halfHeight = rowTopY / 2f;
            float halfDepth = auditoriumLayout.riserDepth() / 2f;
            float seatZ = auditoriumLayout.frontSeatZ() - (rowIndex * auditoriumLayout.rowPitch()) + 0.12f;
            float rowZ = seatZ - (halfDepth - auditoriumLayout.riserBackwardOffset());
            float rowHalfWidth = Math.max(
                resolveConferenceAuditoriumRowHalfWidth(rowIndex, auditoriumLayout) + 0.35f,
                (metrics.roomWidth() / 2f) - 0.62f
            );

            sceneRoot.attachChild(createSimpleBox(
                "conference-riser-" + rowIndex,
                rowHalfWidth,
                halfHeight,
                halfDepth,
                stageMaterial,
                new Vector3f(0f, halfHeight, rowZ)
            ));
            sceneRoot.attachChild(createSimpleBox(
                "conference-riser-trim-" + rowIndex,
                rowHalfWidth,
                0.03f,
                0.03f,
                trimMaterial,
                new Vector3f(0f, rowTopY - 0.03f, rowZ + halfDepth - 0.03f)
            ));
        }
    }

    private Vector3f resolveConferenceUSeatPosition(Room3DPreviewData.SeatPreview seat, LayoutMetrics metrics) {
        ConferenceUShapeLayout uShapeLayout = buildConferenceUShapeLayout(metrics);
        int minColumn = previewData.minColumn();
        int maxColumn = previewData.maxColumn();
        int minRow = previewData.minRow();
        int maxRow = previewData.maxRow();

        if (seat.column() == minColumn) {
            float rowFactor = computeNormalizedIndex(seat.row(), minRow, maxRow);
            float z = interpolate(uShapeLayout.sideSeatFrontZ(), uShapeLayout.sideSeatBackZ(), rowFactor);
            return new Vector3f(uShapeLayout.leftSeatX(), 0f, z);
        }

        if (seat.column() == maxColumn) {
            float rowFactor = computeNormalizedIndex(seat.row(), minRow, maxRow);
            float z = interpolate(uShapeLayout.sideSeatFrontZ(), uShapeLayout.sideSeatBackZ(), rowFactor);
            return new Vector3f(uShapeLayout.rightSeatX(), 0f, z);
        }

        float columnFactor = computeNormalizedIndex(seat.column(), minColumn + 1, maxColumn - 1);
        float x = interpolate(uShapeLayout.backSeatMinX(), uShapeLayout.backSeatMaxX(), columnFactor);
        return new Vector3f(x, 0f, uShapeLayout.backSeatZ());
    }

    private Vector3f resolveConferenceUMicrophonePosition(
        Room3DPreviewData.SeatPreview seat,
        LayoutMetrics metrics,
        ConferenceUShapeLayout uShapeLayout
    ) {
        int minColumn = previewData.minColumn();
        int maxColumn = previewData.maxColumn();
        int minRow = previewData.minRow();
        int maxRow = previewData.maxRow();
        float microphoneSurfaceY = 0.77f;

        if (seat.column() == minColumn) {
            float rowFactor = computeNormalizedIndex(seat.row(), minRow, maxRow);
            float z = interpolate(uShapeLayout.sideSeatFrontZ(), uShapeLayout.sideSeatBackZ(), rowFactor);
            float x = uShapeLayout.leftTableX() - (uShapeLayout.sideTableWidth() / 2f) + 0.09f;
            return new Vector3f(x, microphoneSurfaceY, z);
        }

        if (seat.column() == maxColumn) {
            float rowFactor = computeNormalizedIndex(seat.row(), minRow, maxRow);
            float z = interpolate(uShapeLayout.sideSeatFrontZ(), uShapeLayout.sideSeatBackZ(), rowFactor);
            float x = uShapeLayout.rightTableX() + (uShapeLayout.sideTableWidth() / 2f) - 0.09f;
            return new Vector3f(x, microphoneSurfaceY, z);
        }

        float columnFactor = computeNormalizedIndex(seat.column(), minColumn + 1, maxColumn - 1);
        float x = interpolate(uShapeLayout.backSeatMinX(), uShapeLayout.backSeatMaxX(), columnFactor);
        float z = uShapeLayout.backTableZ() - (uShapeLayout.backTableDepth() / 2f) + 0.1f;
        return new Vector3f(x, microphoneSurfaceY, z);
    }

    private Vector3f resolveReunionMicrophonePosition(
        Room3DPreviewData.SeatPreview seat,
        LayoutMetrics metrics,
        ReunionTableLayout reunionTableLayout
    ) {
        float microphoneSurfaceY = 0.77f;
        ReunionSeatSlot seatSlot = resolveReunionSeatSlot(seat);

        return switch (seatSlot.side()) {
            case FRONT -> new Vector3f(
                interpolate(reunionTableLayout.frontSeatMinX(), reunionTableLayout.frontSeatMaxX(), seatSlot.factor()),
                microphoneSurfaceY,
                reunionTableLayout.tableZ() + (reunionTableLayout.tableDepth() / 2f) - 0.1f
            );
            case BACK -> new Vector3f(
                interpolate(reunionTableLayout.backSeatMinX(), reunionTableLayout.backSeatMaxX(), seatSlot.factor()),
                microphoneSurfaceY,
                reunionTableLayout.tableZ() - (reunionTableLayout.tableDepth() / 2f) + 0.1f
            );
            case LEFT -> new Vector3f(
                -(reunionTableLayout.tableWidth() / 2f) + 0.09f,
                microphoneSurfaceY,
                interpolate(reunionTableLayout.sideSeatFrontZ(), reunionTableLayout.sideSeatBackZ(), seatSlot.factor())
            );
            case RIGHT -> new Vector3f(
                (reunionTableLayout.tableWidth() / 2f) - 0.09f,
                microphoneSurfaceY,
                interpolate(reunionTableLayout.sideSeatFrontZ(), reunionTableLayout.sideSeatBackZ(), seatSlot.factor())
            );
        };
    }

    private Node createSeatNode(Room3DPreviewData.SeatPreview seat, String disposition) {
        Node seatNode = new Node("seat-" + seat.number());
        seatNode.setUserData(SEAT_LABEL_KEY, seat.label());
        seatNode.setUserData(SEAT_STATUS_KEY, seat.state().displayLabel());

        Material seatMaterial = resolveSeatMaterial(seat.state());
        if (isTieredAuditoriumLayout()) {
            attachAuditoriumSeatGeometry(seatNode, seat.number(), seatMaterial);
        } else {
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
        }

        if (shouldAttachIndividualDesk(disposition)) {
            Node deskNode = createSeatDeskNode(seat.number(), disposition);
            deskNode.setLocalTranslation(0f, 0f, 0.6f);
            seatNode.attachChild(deskNode);
        }

        Geometry interactionIndicator = createSeatIndicator(seat.number());
        seatNode.attachChild(interactionIndicator);
        seatVisuals.put(seatNode, new SeatVisual(seat, interactionIndicator));

        return seatNode;
    }

    private void attachAuditoriumSeatGeometry(Node seatNode, int seatNumber, Material seatMaterial) {
        seatNode.attachChild(createSimpleBox(
            "auditorium-seat-base-" + seatNumber,
            0.2f,
            0.055f,
            0.18f,
            seatMaterial,
            new Vector3f(0f, 0.42f, -0.015f)
        ));
        seatNode.attachChild(createSimpleBox(
            "auditorium-seat-back-" + seatNumber,
            0.2f,
            0.24f,
            0.04f,
            seatMaterial,
            new Vector3f(0f, 0.69f, -0.17f)
        ));
        seatNode.attachChild(createSimpleBox(
            "auditorium-seat-left-arm-" + seatNumber,
            0.028f,
            0.14f,
            0.17f,
            trimMaterial,
            new Vector3f(-0.235f, 0.53f, -0.02f)
        ));
        seatNode.attachChild(createSimpleBox(
            "auditorium-seat-right-arm-" + seatNumber,
            0.028f,
            0.14f,
            0.17f,
            trimMaterial,
            new Vector3f(0.235f, 0.53f, -0.02f)
        ));
        seatNode.attachChild(createSimpleBox(
            "auditorium-seat-left-support-" + seatNumber,
            0.022f,
            0.23f,
            0.035f,
            metalMaterial,
            new Vector3f(-0.2f, 0.23f, -0.02f)
        ));
        seatNode.attachChild(createSimpleBox(
            "auditorium-seat-right-support-" + seatNumber,
            0.022f,
            0.23f,
            0.035f,
            metalMaterial,
            new Vector3f(0.2f, 0.23f, -0.02f)
        ));
        seatNode.attachChild(createSimpleBox(
            "auditorium-seat-foot-" + seatNumber,
            0.2f,
            0.018f,
            0.07f,
            metalMaterial,
            new Vector3f(0f, 0.03f, 0.02f)
        ));
    }

    private void orientSeatNode(Node seatNode, Room3DPreviewData.SeatPreview seat, String disposition) {
        if ("u".equals(disposition)) {
            applyUShapeSeatOrientation(seatNode, seat);
            return;
        }

        if (isTieredAuditoriumLayout()) {
            applyConferenceAuditoriumSeatOrientation(seatNode, seat);
            return;
        }

        if ("reunion".equals(disposition)) {
            applyReunionSeatOrientation(seatNode, seat);
        }
    }

    private void applyConferenceAuditoriumSeatOrientation(Node seatNode, Room3DPreviewData.SeatPreview seat) {
        LayoutMetrics metrics = buildLayoutMetrics(previewData);
        ConferenceAuditoriumLayout auditoriumLayout = buildConferenceAuditoriumLayout(metrics);
        if (auditoriumLayout.focusPullFactor() <= 0.001f) {
            return;
        }

        Vector3f seatPosition = resolveConferenceAuditoriumSeatPosition(seat, metrics);
        float yaw = (float) Math.atan2(
            -seatPosition.x * auditoriumLayout.focusPullFactor(),
            auditoriumLayout.focusZ() - seatPosition.z
        );
        seatNode.rotate(0f, yaw, 0f);
    }

    private void applyReunionSeatOrientation(Node seatNode, Room3DPreviewData.SeatPreview seat) {
        ReunionSeatSlot seatSlot = resolveReunionSeatSlot(seat);
        switch (seatSlot.side()) {
            case FRONT -> seatNode.rotate(0f, HALF_TURN_Y, 0f);
            case LEFT -> seatNode.rotate(0f, QUARTER_TURN_Y, 0f);
            case RIGHT -> seatNode.rotate(0f, -QUARTER_TURN_Y, 0f);
            case BACK -> {
            }
        }
    }

    private void applyUShapeSeatOrientation(Node seatNode, Room3DPreviewData.SeatPreview seat) {
        if (seat.row() == previewData.maxRow()) {
            return;
        }

        if (seat.column() == previewData.minColumn()) {
            seatNode.rotate(0f, QUARTER_TURN_Y, 0f);
            return;
        }

        if (seat.column() == previewData.maxColumn()) {
            seatNode.rotate(0f, -QUARTER_TURN_Y, 0f);
        }
    }

    private boolean shouldAttachIndividualDesk(String disposition) {
        if ("reunion".equals(disposition) || isTieredAuditoriumLayout()) {
            return false;
        }

        return !isConferenceUShapeLayout();
    }

    private boolean isConferenceUShapeLayout() {
        return "u".equals(normalize(previewData.disposition()))
            && "conference".equals(normalize(previewData.roomType()));
    }

    private boolean isConferenceAuditoriumLayout() {
        return "conference".equals(normalize(previewData.disposition()))
            && "conference".equals(normalize(previewData.roomType()));
    }

    private boolean isAmphitheatreConferenceLayout() {
        return "conference".equals(normalize(previewData.disposition()))
            && "amphitheatre".equals(normalize(previewData.roomType()));
    }

    private boolean isTieredAuditoriumLayout() {
        return isConferenceAuditoriumLayout() || isAmphitheatreConferenceLayout();
    }

    private ConferenceAuditoriumLayout buildConferenceAuditoriumLayout(LayoutMetrics metrics) {
        boolean amphitheatreLayout = isAmphitheatreConferenceLayout();
        int rowCount = Math.max(1, previewData.rowSpan());
        int columnCount = Math.max(1, previewData.columnSpan());
        float seatSpacingX = amphitheatreLayout
            ? (columnCount >= 10 ? 0.5f : 0.515f)
            : (columnCount >= 8 ? 0.56f : 0.535f);
        float rowPitch = amphitheatreLayout
            ? Math.max(1.24f, metrics.seatSpacingZ() - 0.46f)
            : Math.max(1.12f, metrics.seatSpacingZ() - 0.3f);
        float rowRise = amphitheatreLayout ? 0.28f : 0.19f;
        float centralAisleWidth = amphitheatreLayout
            ? (columnCount >= 10 ? 0.92f : columnCount >= 6 ? 0.76f : 0f)
            : (columnCount >= 8 ? 0.78f : 0f);
        float rowFanOut = amphitheatreLayout
            ? (columnCount >= 7 ? 0.045f : 0.028f)
            : 0f;
        float frontSeatZ = ((rowCount - 1) * rowPitch) / 2f;
        float riserDepth = amphitheatreLayout
            ? Math.max(1.22f, rowPitch * 0.98f)
            : Math.max(1.08f, rowPitch * 0.92f);
        float riserBackwardOffset = amphitheatreLayout ? 0.24f : 0.18f;
        float focusZ = frontSeatZ + (amphitheatreLayout ? 3.35f : 2.9f);
        float focusPullFactor = amphitheatreLayout ? 0.08f : 0f;
        float frontRowHalfWidth = resolveConferenceAuditoriumRowHalfWidth(
            0,
            columnCount,
            seatSpacingX,
            centralAisleWidth,
            rowFanOut
        );

        return new ConferenceAuditoriumLayout(
            rowCount,
            columnCount,
            seatSpacingX,
            rowPitch,
            rowRise,
            frontSeatZ,
            centralAisleWidth,
            rowFanOut,
            riserDepth,
            riserBackwardOffset,
            focusZ,
            focusPullFactor,
            frontRowHalfWidth,
            frontSeatZ + (amphitheatreLayout ? 2.28f : 1.72f)
        );
    }

    private float resolveConferenceAuditoriumSeatX(
        int columnIndex,
        int rowIndex,
        ConferenceAuditoriumLayout auditoriumLayout
    ) {
        float x = (columnIndex - ((auditoriumLayout.columnCount() - 1) / 2f)) * auditoriumLayout.seatSpacingX();
        if (auditoriumLayout.centralAisleWidth() > 0f) {
            if (columnIndex >= auditoriumLayout.columnCount() / 2) {
                x += auditoriumLayout.centralAisleWidth() / 2f;
            } else {
                x -= auditoriumLayout.centralAisleWidth() / 2f;
            }
        }

        float fanOut = rowIndex * auditoriumLayout.rowFanOut();
        if (x > 0f) {
            return x + fanOut;
        }
        if (x < 0f) {
            return x - fanOut;
        }
        return x;
    }

    private float resolveConferenceAuditoriumRowHalfWidth(int rowIndex, ConferenceAuditoriumLayout auditoriumLayout) {
        return resolveConferenceAuditoriumRowHalfWidth(
            rowIndex,
            auditoriumLayout.columnCount(),
            auditoriumLayout.seatSpacingX(),
            auditoriumLayout.centralAisleWidth(),
            auditoriumLayout.rowFanOut()
        );
    }

    private float resolveConferenceAuditoriumRowHalfWidth(
        int rowIndex,
        int columnCount,
        float seatSpacingX,
        float centralAisleWidth,
        float rowFanOut
    ) {
        if (columnCount <= 0) {
            return 2.4f;
        }

        float leftX = resolveConferenceAuditoriumSeatX(
            0,
            rowIndex,
            new ConferenceAuditoriumLayout(
                1,
                columnCount,
                seatSpacingX,
                1f,
                0f,
                0f,
                centralAisleWidth,
                rowFanOut,
                0f,
                0f,
                0f,
                0f,
                0f,
                0f
            )
        );
        float rightX = resolveConferenceAuditoriumSeatX(
            columnCount - 1,
            rowIndex,
            new ConferenceAuditoriumLayout(
                1,
                columnCount,
                seatSpacingX,
                1f,
                0f,
                0f,
                centralAisleWidth,
                rowFanOut,
                0f,
                0f,
                0f,
                0f,
                0f,
                0f
            )
        );
        return Math.max(2.4f, Math.max(Math.abs(leftX), Math.abs(rightX)) + 0.58f);
    }

    private ConferenceUShapeLayout buildConferenceUShapeLayout(LayoutMetrics metrics) {
        float sideTableWidth = 0.72f;
        float backTableDepth = 0.74f;
        float sideInset = 0.84f;
        float chairOffset = 0.62f;
        float frontOpeningDepth = Math.max(1.4f, metrics.seatSpacingZ() * 0.72f);

        float leftTableX = -(metrics.layoutWidth() / 2f) + sideInset;
        float rightTableX = (metrics.layoutWidth() / 2f) - sideInset;
        float backTableZ = -(metrics.layoutDepth() / 2f) + 0.82f;
        float backSeatZ = backTableZ - (backTableDepth / 2f) - chairOffset;
        float sideSeatBackZ = backSeatZ;
        float sideSeatFrontZ = Math.max(sideSeatBackZ + metrics.seatSpacingZ(), metrics.frontSeatZ() - frontOpeningDepth);
        float sideTableCenterZ = (sideSeatFrontZ + sideSeatBackZ) / 2f;
        float sideTableDepth = Math.max(3.6f, (sideSeatFrontZ - sideSeatBackZ) + 0.45f);
        float leftSeatX = leftTableX - (sideTableWidth / 2f) - chairOffset;
        float rightSeatX = rightTableX + (sideTableWidth / 2f) + chairOffset;
        float backSeatMinX = leftTableX + (sideTableWidth / 2f) + 0.28f;
        float backSeatMaxX = rightTableX - (sideTableWidth / 2f) - 0.28f;
        float backTableWidth = Math.max(3.5f, (backSeatMaxX - backSeatMinX) + 1.45f);

        return new ConferenceUShapeLayout(
            sideTableWidth,
            sideTableDepth,
            sideTableCenterZ,
            leftTableX,
            rightTableX,
            backTableWidth,
            backTableDepth,
            backTableZ,
            leftSeatX,
            rightSeatX,
            backSeatZ,
            sideSeatFrontZ,
            sideSeatBackZ,
            backSeatMinX,
            backSeatMaxX
        );
    }

    private ReunionTableLayout buildReunionTableLayout(LayoutMetrics metrics) {
        java.util.List<ReunionSeatSlot> seatSlots = buildReunionSeatSlots(previewData.seatCount());
        int frontSeatCount = 0;
        int backSeatCount = 0;
        int leftSeatCount = 0;
        int rightSeatCount = 0;
        for (ReunionSeatSlot seatSlot : seatSlots) {
            switch (seatSlot.side()) {
                case FRONT -> frontSeatCount++;
                case BACK -> backSeatCount++;
                case LEFT -> leftSeatCount++;
                case RIGHT -> rightSeatCount++;
            }
        }

        int horizontalSeatCount = Math.max(1, Math.max(frontSeatCount, backSeatCount));
        int verticalSeatCount = Math.max(1, Math.max(leftSeatCount, rightSeatCount));
        float tableZ = 0f;
        float frontBackChairOffset = 0.44f;
        float sideChairOffset = 0.34f;
        float rowCornerInset = 0.34f + Math.min(0.22f, Math.max(0, horizontalSeatCount - 3) * 0.04f);
        float sideCornerInset = 0.3f + Math.min(0.18f, Math.max(0, verticalSeatCount - 2) * 0.05f);
        float targetFrontBackSeatSpacing = 0.84f;
        float targetSideSeatSpacing = 0.82f;
        float desiredUsableWidth = Math.max(0f, (horizontalSeatCount - 1) * targetFrontBackSeatSpacing);
        float desiredUsableDepth = Math.max(0f, (verticalSeatCount - 1) * targetSideSeatSpacing);
        float minTableWidth = Math.max(3.1f, metrics.layoutWidth() * 0.76f);
        float minTableDepth = Math.max(2.05f, metrics.layoutDepth() * 0.68f);
        float maxTableWidth = Math.max(minTableWidth, metrics.roomWidth() - 3.6f);
        float maxTableDepth = Math.max(minTableDepth, metrics.roomDepth() - 3.8f);
        float tableWidth = clamp(
            desiredUsableWidth + (rowCornerInset * 2f) + 0.36f,
            minTableWidth,
            maxTableWidth
        );
        float tableDepth = clamp(
            desiredUsableDepth + (sideCornerInset * 2f) + 0.42f,
            minTableDepth,
            maxTableDepth
        );
        float frontSeatZ = tableZ + (tableDepth / 2f) + frontBackChairOffset;
        float backSeatZ = tableZ - (tableDepth / 2f) - frontBackChairOffset;
        float leftSeatX = -(tableWidth / 2f) - sideChairOffset;
        float rightSeatX = (tableWidth / 2f) + sideChairOffset;
        float frontSeatMinX = -(tableWidth / 2f) + rowCornerInset;
        float frontSeatMaxX = (tableWidth / 2f) - rowCornerInset;
        float backSeatMinX = frontSeatMinX;
        float backSeatMaxX = frontSeatMaxX;
        float sideSeatFrontZ = tableZ + (tableDepth / 2f) - sideCornerInset;
        float sideSeatBackZ = tableZ - (tableDepth / 2f) + sideCornerInset;

        return new ReunionTableLayout(
            tableWidth,
            tableDepth,
            tableZ,
            frontSeatZ,
            backSeatZ,
            leftSeatX,
            rightSeatX,
            frontSeatMinX,
            frontSeatMaxX,
            backSeatMinX,
            backSeatMaxX,
            sideSeatFrontZ,
            sideSeatBackZ
        );
    }

    private ReunionSeatSlot resolveReunionSeatSlot(Room3DPreviewData.SeatPreview seat) {
        java.util.List<ReunionSeatSlot> seatSlots = buildReunionSeatSlots(previewData.seatCount());
        if (seatSlots.isEmpty()) {
            return new ReunionSeatSlot(ReunionSeatSide.BACK, 0.5f);
        }

        int seatIndex = previewData.seats().indexOf(seat);
        if (seatIndex < 0) {
            seatIndex = Math.max(0, Math.min(seatSlots.size() - 1, seat.number() - 1));
        }

        return seatSlots.get(Math.max(0, Math.min(seatSlots.size() - 1, seatIndex)));
    }

    private java.util.List<ReunionSeatSlot> buildReunionSeatSlots(int seatCount) {
        if (seatCount <= 0) {
            return java.util.List.of();
        }

        int rows = 4;
        int columns = 4;
        while (calculateReunionPerimeterSeatCount(rows, columns) < seatCount) {
            if (columns <= rows) {
                columns++;
            } else {
                rows++;
            }
        }

        java.util.List<ReunionSeatSlot> perimeterSlots = new java.util.ArrayList<>(calculateReunionPerimeterSeatCount(rows, columns));
        addReunionSeatSlots(perimeterSlots, ReunionSeatSide.FRONT, columns, false);
        addReunionSeatSlots(perimeterSlots, ReunionSeatSide.RIGHT, Math.max(0, rows - 2), false);
        addReunionSeatSlots(perimeterSlots, ReunionSeatSide.BACK, columns, true);
        addReunionSeatSlots(perimeterSlots, ReunionSeatSide.LEFT, Math.max(0, rows - 2), true);

        if (seatCount >= perimeterSlots.size()) {
            return perimeterSlots;
        }

        java.util.List<ReunionSeatSlot> balancedSlots = new java.util.ArrayList<>(seatCount);
        for (int index = 0; index < seatCount; index++) {
            int slotIndex = Math.min(
                perimeterSlots.size() - 1,
                (int) Math.floor(((index + 0.5f) * perimeterSlots.size()) / seatCount)
            );
            balancedSlots.add(perimeterSlots.get(slotIndex));
        }
        return balancedSlots;
    }

    private void addReunionSeatSlots(
        java.util.List<ReunionSeatSlot> seatSlots,
        ReunionSeatSide side,
        int seatCount,
        boolean reverseFactor
    ) {
        if (seatCount <= 0) {
            return;
        }

        for (int index = 0; index < seatCount; index++) {
            float factor = seatCount == 1 ? 0.5f : index / (float) (seatCount - 1);
            seatSlots.add(new ReunionSeatSlot(side, reverseFactor ? 1f - factor : factor));
        }
    }

    private float computeNormalizedIndex(int value, int min, int max) {
        if (max <= min) {
            return 0.5f;
        }
        return (value - min) / (float) (max - min);
    }

    private int calculateReunionPerimeterSeatCount(int rows, int columns) {
        if (rows <= 0 || columns <= 0) {
            return 0;
        }
        if (rows == 1) {
            return columns;
        }
        if (columns == 1) {
            return rows;
        }
        return (rows * 2) + (columns * 2) - 4;
    }

    private float resolveReunionMicrophoneYaw(Room3DPreviewData.SeatPreview seat) {
        return switch (resolveReunionSeatSlot(seat).side()) {
            case FRONT -> 0f;
            case BACK -> HALF_TURN_Y;
            case LEFT -> -QUARTER_TURN_Y;
            case RIGHT -> QUARTER_TURN_Y;
        };
    }

    private float resolveConferenceUMicrophoneYaw(Room3DPreviewData.SeatPreview seat) {
        if (seat.column() == previewData.minColumn()) {
            return -QUARTER_TURN_Y;
        }
        if (seat.column() == previewData.maxColumn()) {
            return QUARTER_TURN_Y;
        }
        return HALF_TURN_Y;
    }

    private Node createConferenceMicrophoneNode(String name, float yaw) {
        Node microphoneNode = new Node(name);
        microphoneNode.attachChild(createSimpleBox(
            name + "-base",
            0.045f,
            0.01f,
            0.03f,
            metalMaterial,
            new Vector3f(0f, 0.01f, 0f)
        ));
        microphoneNode.attachChild(createSimpleBox(
            name + "-stem",
            0.006f,
            0.06f,
            0.006f,
            metalMaterial,
            new Vector3f(0f, 0.075f, 0f)
        ));
        microphoneNode.attachChild(createSimpleBox(
            name + "-arm",
            0.005f,
            0.005f,
            0.05f,
            metalMaterial,
            new Vector3f(0f, 0.135f, 0.04f)
        ));
        microphoneNode.attachChild(createSimpleBox(
            name + "-head",
            0.012f,
            0.008f,
            0.012f,
            screenMaterial,
            new Vector3f(0f, 0.14f, 0.09f)
        ));
        microphoneNode.attachChild(createSimpleBox(
            name + "-indicator",
            0.005f,
            0.005f,
            0.005f,
            accentMaterial,
            new Vector3f(0f, 0.145f, 0.105f)
        ));
        microphoneNode.rotate(0f, yaw, 0f);
        return microphoneNode;
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

    private Node createConferenceLecternNode() {
        Node lecternNode = new Node("conference-lectern");
        lecternNode.attachChild(createSimpleBox(
            "conference-lectern-body",
            0.18f,
            0.56f,
            0.17f,
            storageMaterial,
            new Vector3f(0f, 0.56f, 0f)
        ));
        lecternNode.attachChild(createSimpleBox(
            "conference-lectern-top",
            0.28f,
            0.025f,
            0.2f,
            deskMaterial,
            new Vector3f(0f, 1.12f, 0.04f)
        ));
        lecternNode.attachChild(createSimpleBox(
            "conference-lectern-screen",
            0.14f,
            0.014f,
            0.1f,
            screenMaterial,
            new Vector3f(0f, 1.15f, -0.02f)
        ));
        lecternNode.attachChild(createSimpleBox(
            "conference-lectern-mic-base",
            0.024f,
            0.01f,
            0.024f,
            metalMaterial,
            new Vector3f(0f, 1.17f, 0.06f)
        ));
        lecternNode.attachChild(createSimpleBox(
            "conference-lectern-mic-stem",
            0.008f,
            0.09f,
            0.008f,
            metalMaterial,
            new Vector3f(0f, 1.26f, 0.06f)
        ));
        return lecternNode;
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
                pointLight.setColor(new ColorRGBA(1f, 0.97f, 0.94f, 1f).mult(0.48f));
                pointLight.setRadius(Math.max(6.2f, metrics.roomWidth() * 0.72f));
                pointLight.setPosition(new Vector3f(x, WALL_HEIGHT - 0.32f, z));
                sceneRoot.addLight(pointLight);
            }
        }
    }

    private void attachLights() {
        AmbientLight ambientLight = new AmbientLight();
        ambientLight.setColor(new ColorRGBA(0.98f, 0.98f, 1f, 1f).mult(0.34f));
        sceneRoot.addLight(ambientLight);

        primaryShadowLight = new DirectionalLight();
        primaryShadowLight.setDirection(new Vector3f(-0.38f, -0.82f, -0.3f).normalizeLocal());
        primaryShadowLight.setColor(new ColorRGBA(1f, 0.97f, 0.93f, 1f).mult(0.56f));
        sceneRoot.addLight(primaryShadowLight);
        if (shadowRenderer != null) {
            shadowRenderer.setLight(primaryShadowLight);
        }

        DirectionalLight fillLight = new DirectionalLight();
        fillLight.setDirection(new Vector3f(0.52f, -0.72f, 0.24f).normalizeLocal());
        fillLight.setColor(new ColorRGBA(0.9f, 0.95f, 1f, 1f).mult(0.18f));
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
        boolean amphitheatreLayout = isAmphitheatreConferenceLayout();
        float maxDimension = Math.max(metrics.roomWidth(), metrics.roomDepth());
        float halfRoomWidth = metrics.roomWidth() / 2f;
        float halfRoomDepth = metrics.roomDepth() / 2f;
        float screenCenterZ = Math.min(halfRoomDepth - 0.82f, metrics.frontSeatZ() + 2.43f);
        float backWallClearance = Math.max(0.42f, metrics.seatSpacingZ() * 0.16f);
        float backCameraZ = -halfRoomDepth + backWallClearance;
        float backFocusZ = Math.min(screenCenterZ - 0.02f, metrics.frontSeatZ() + 1.85f);
        float backCameraY = amphitheatreLayout
            ? Math.min(2.86f, Math.max(2.26f, maxDimension * 0.21f))
            : Math.min(2.32f, Math.max(2.02f, maxDimension * 0.19f));
        float backFocusY = amphitheatreLayout ? 1.52f : 1.34f;
        // Keep the teacher view clearly in front of the board surface, not intersecting it.
        float teacherBoardClearance = amphitheatreLayout
            ? Math.max(1.12f, metrics.seatSpacingZ() * 0.56f)
            : Math.max(0.92f, metrics.seatSpacingZ() * 0.58f);
        float teacherCameraZ = Math.min(
            screenCenterZ - teacherBoardClearance,
            halfRoomDepth - 0.36f
        );
        float teacherFocusZ = -Math.max(2.05f, metrics.layoutDepth() * 0.7f);
        float teacherCameraY = amphitheatreLayout
            ? Math.min(2.3f, Math.max(2.02f, maxDimension * 0.18f))
            : Math.min(2.08f, Math.max(1.92f, maxDimension * 0.17f));
        float teacherFocusY = amphitheatreLayout ? 1.38f : 1.18f;

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
                    0f,
                    backCameraY,
                    backCameraZ
                ),
                new Vector3f(0f, backFocusY, backFocusZ),
                SHOWCASE_CAMERA_FOV + 10f
            );
            case TEACHER -> new CameraView(
                new Vector3f(
                    0f,
                    teacherCameraY,
                    teacherCameraZ
                ),
                new Vector3f(
                    0f,
                    teacherFocusY,
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
        updateCameraPresetButtonStyles();

        if (animate) {
            startCameraTransition(targetView);
            return;
        }

        updateCeilingVisibilityForPreset(preset);
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
            updateCeilingVisibilityForPreset(activeCameraPreset);
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
        boolean entranceView = preset == CameraPreset.ENTRANCE;

        if (ceilingCutaway != null) {
            Spatial.CullHint ceilingCullHint =
                preset == CameraPreset.TOP || entranceView
                    ? Spatial.CullHint.Always
                    : Spatial.CullHint.Inherit;

            ceilingCutaway.setCullHint(ceilingCullHint);
        }
        if (ceilingLightPanels != null) {
            ceilingLightPanels.setCullHint(
                preset == CameraPreset.TOP || entranceView
                    ? Spatial.CullHint.Always
                    : Spatial.CullHint.Inherit
            );
        }
        if (frontWall != null) {
            frontWall.setCullHint(entranceView ? Spatial.CullHint.Always : Spatial.CullHint.Inherit);
        }
        if (frontBorder != null) {
            frontBorder.setCullHint(entranceView ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
        }
        if (frontBorderTrim != null) {
            frontBorderTrim.setCullHint(entranceView ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
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

    private float clamp(float value, float min, float max) {
        if (max <= min) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
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
        boolean amphitheatreLayout = isAmphitheatreConferenceLayout();

        float seatSpacingX;
        float seatSpacingZ;
        if (amphitheatreLayout) {
            seatSpacingX = 1.48f;
            seatSpacingZ = 2.02f;
        } else if (isTieredAuditoriumLayout()) {
            seatSpacingX = 1.34f;
            seatSpacingZ = 1.82f;
        } else {
            seatSpacingX = switch (disposition) {
                case "conference" -> 1.45f;
                case "informatique" -> 1.5f;
                case "u", "reunion" -> 1.65f;
                default -> 1.3f;
            };
            seatSpacingZ = switch (disposition) {
                case "conference" -> 1.55f;
                case "u" -> 1.75f;
                case "reunion" -> 1.45f;
                default -> 1.35f;
            };
        }

        int minRow = previewData.minRow();
        int minColumn = previewData.minColumn();
        float layoutWidth = Math.max(2.6f, Math.max(0, previewData.columnSpan() - 1) * seatSpacingX);
        float layoutDepth = Math.max(2.6f, Math.max(0, previewData.rowSpan() - 1) * seatSpacingZ);
        float roomWidth = layoutWidth + (amphitheatreLayout ? 6.4f : 5.2f);
        float roomDepth = layoutDepth + (amphitheatreLayout ? 8.4f : 6.6f);
        float frontSeatZ = layoutDepth / 2f;

        return new LayoutMetrics(seatSpacingX, seatSpacingZ, layoutWidth, layoutDepth, roomWidth, roomDepth, frontSeatZ, minRow, minColumn);
    }

    private String buildDefaultSummary() {
        return "";
    }

    private String buildDefaultSelectionText() {
        return "";
    }

    private String buildLegendText() {
        return "";
    }

    private String buildInteractionHintText() {
        if (previewData.supportsSeatSelection()) {
            return "Guide: clic pour choisir | glisser pour deplacer la camera | boutons camera | ZQSD/WASD";
        }
        return "Guide: clic pour voir une place | glisser pour deplacer la camera | boutons camera | ZQSD/WASD";
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
            setSelectedSeatSnapshot(null, null);
            selectionText.setText(buildUnavailableSelectionText(seat));
            selectionText.setColor(resolveSeatHudColor(seat == null ? RoomSeatVisualState.UNAVAILABLE : seat.state()));
            updateBitmapTextVisibility(selectionText);
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
            setSelectedSeatSnapshot(null, null);
            updateBitmapTextVisibility(selectionText);
            updateHudPositions();
            return;
        }

        SeatVisual seatVisual = seatVisuals.get(selectedSeatNode);
        if (seatVisual == null) {
            selectionText.setText(buildDefaultSelectionText());
            selectionText.setColor(createHudNeutralColor());
            setSelectedSeatSnapshot(null, null);
            updateBitmapTextVisibility(selectionText);
            updateHudPositions();
            return;
        }

        Room3DPreviewData.SeatPreview seat = seatVisual.seat();
        selectionText.setText(buildSelectionText(seat));
        selectionText.setColor(resolveSeatInfoHudColor(seat));
        updateSelectionSnapshot(seat);
        updateBitmapTextVisibility(selectionText);
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
        hoverText.setText(buildHoverText(seat));
        hoverText.setColor(resolveSeatInfoHudColor(seat));
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

    private ColorRGBA resolveSeatInfoHudColor(Room3DPreviewData.SeatPreview seat) {
        if (!shouldShowSeatStateInHud()) {
            return createHudSelectionColor();
        }
        return resolveSeatHudColor(seat == null ? null : seat.state());
    }

    private boolean shouldShowSeatStateInHud() {
        return previewData != null && previewData.supportsSeatSelection();
    }

    private ColorRGBA createHudNeutralColor() {
        return new ColorRGBA(0.29f, 0.24f, 0.19f, 1f);
    }

    private ColorRGBA createHudSelectionColor() {
        return new ColorRGBA(0.23f, 0.15f, 0.08f, 1f);
    }

    private void updateBitmapTextVisibility(BitmapText text) {
        if (text == null) {
            return;
        }

        text.setCullHint(text.getText() == null || text.getText().isBlank()
            ? Spatial.CullHint.Always
            : Spatial.CullHint.Inherit);
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
            return "Selection impossible";
        }
        return seat.label() + " | " + seat.state().displayLabel() + " | impossible";
    }

    private String buildSelectionText(Room3DPreviewData.SeatPreview seat) {
        if (seat == null) {
            return buildDefaultSelectionText();
        }

        String prefix = previewData.supportsSeatSelection() && seat.selectable() ? "Choix: " : "Selection: ";
        if (!shouldShowSeatStateInHud()) {
            return prefix + seat.label();
        }
        return prefix + seat.label() + " | " + seat.state().displayLabel();
    }

    private String buildHoverText(Room3DPreviewData.SeatPreview seat) {
        if (seat == null) {
            return "";
        }

        if (!shouldShowSeatStateInHud()) {
            return seat.label();
        }
        return seat.label() + "\nEtat: " + seat.state().displayLabel();
    }

    private void updateSelectionSnapshot(Room3DPreviewData.SeatPreview seat) {
        if (seat == null || !previewData.supportsSeatSelection() || !seat.selectable() || !seat.hasPersistentId()) {
            setSelectedSeatSnapshot(null, null);
            return;
        }

        setSelectedSeatSnapshot(seat.seatId(), seat.label());
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

    private record ConferenceUShapeLayout(
        float sideTableWidth,
        float sideTableDepth,
        float sideTableCenterZ,
        float leftTableX,
        float rightTableX,
        float backTableWidth,
        float backTableDepth,
        float backTableZ,
        float leftSeatX,
        float rightSeatX,
        float backSeatZ,
        float sideSeatFrontZ,
        float sideSeatBackZ,
        float backSeatMinX,
        float backSeatMaxX
    ) {
    }

    private record ReunionTableLayout(
        float tableWidth,
        float tableDepth,
        float tableZ,
        float frontSeatZ,
        float backSeatZ,
        float leftSeatX,
        float rightSeatX,
        float frontSeatMinX,
        float frontSeatMaxX,
        float backSeatMinX,
        float backSeatMaxX,
        float sideSeatFrontZ,
        float sideSeatBackZ
    ) {
    }

    private record ConferenceAuditoriumLayout(
        int rowCount,
        int columnCount,
        float seatSpacingX,
        float rowPitch,
        float rowRise,
        float frontSeatZ,
        float centralAisleWidth,
        float rowFanOut,
        float riserDepth,
        float riserBackwardOffset,
        float focusZ,
        float focusPullFactor,
        float frontRowHalfWidth,
        float stageZoneZ
    ) {
    }

    private record ReunionSeatSlot(ReunionSeatSide side, float factor) {
    }

    private enum ReunionSeatSide {
        FRONT,
        RIGHT,
        BACK,
        LEFT
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
