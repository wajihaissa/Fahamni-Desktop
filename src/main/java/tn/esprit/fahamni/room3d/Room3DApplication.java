package tn.esprit.fahamni.room3d;

import com.jme3.app.SimpleApplication;
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
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import com.jme3.collision.CollisionResults;

import java.util.Locale;
import java.util.Objects;

public class Room3DApplication extends SimpleApplication {

    private static final String SELECT_SEAT_MAPPING = "select-room-seat";
    private static final String SEAT_LABEL_KEY = "seatLabel";
    private static final String SEAT_STATUS_KEY = "seatStatus";

    private static final float WALL_HEIGHT = 3.8f;
    private static final float CEILING_THICKNESS = 0.05f;
    private static final float FLOOR_THICKNESS = 0.08f;
    private static final float FRONT_BORDER_HEIGHT = 0.22f;

    private Room3DPreviewData previewData;
    private Node sceneRoot;
    private BitmapFont hudFont;
    private BitmapText titleText;
    private BitmapText summaryText;
    private BitmapText interactionHintText;
    private boolean initialized;

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
    private Material availableSeatMaterial;
    private Material reservedSeatMaterial;
    private Material maintenanceSeatMaterial;
    private Material unavailableSeatMaterial;

    private final ActionListener selectionListener = (name, isPressed, tpf) -> {
        if (!SELECT_SEAT_MAPPING.equals(name) || !isPressed || sceneRoot == null) {
            return;
        }

        CollisionResults collisions = new CollisionResults();
        Vector2f cursorPosition = inputManager.getCursorPosition().clone();
        Vector3f rayOrigin = cam.getWorldCoordinates(cursorPosition, 0f);
        Vector3f rayDirection = cam.getWorldCoordinates(cursorPosition, 1f).subtractLocal(rayOrigin).normalizeLocal();
        sceneRoot.collideWith(new Ray(rayOrigin, rayDirection), collisions);

        for (int index = 0; index < collisions.size(); index++) {
            Spatial spatial = collisions.getCollision(index).getGeometry();
            String seatLabel = resolveUserData(spatial, SEAT_LABEL_KEY);
            if (seatLabel == null) {
                continue;
            }

            String seatStatus = resolveUserData(spatial, SEAT_STATUS_KEY);
            summaryText.setText(seatLabel + " | Etat: " + (seatStatus == null ? "inconnu" : seatStatus));
            updateHudPositions();
            return;
        }

        summaryText.setText(buildDefaultSummary());
        updateHudPositions();
    };

    public Room3DApplication(Room3DPreviewData previewData) {
        this.previewData = Objects.requireNonNull(previewData, "previewData");
    }

    @Override
    public void simpleInitApp() {
        initialized = true;
        setDisplayFps(false);
        setDisplayStatView(false);
        viewPort.setBackgroundColor(new ColorRGBA(0.96f, 0.98f, 1f, 1f));

        flyCam.setMoveSpeed(14f);
        flyCam.setDragToRotate(true);

        inputManager.addMapping(SELECT_SEAT_MAPPING, new MouseButtonTrigger(MouseInput.BUTTON_RIGHT));
        inputManager.addListener(selectionListener, SELECT_SEAT_MAPPING);

        hudFont = assetManager.loadFont("Interface/Fonts/Default.fnt");
        initialiseMaterials();
        rebuildScene();
    }

    @Override
    public void reshape(int width, int height) {
        super.reshape(width, height);
        updateHudPositions();
    }

    @Override
    public void destroy() {
        Room3DViewerLauncher.onApplicationClosed(this);
        super.destroy();
    }

    public void updatePreview(Room3DPreviewData previewData) {
        this.previewData = Objects.requireNonNull(previewData, "previewData");
        if (initialized) {
            rebuildScene();
        }
    }

    private void rebuildScene() {
        if (sceneRoot != null) {
            rootNode.detachChild(sceneRoot);
        }
        guiNode.detachAllChildren();

        initialiseHud();

        sceneRoot = new Node("room-3d-preview-root");
        rootNode.attachChild(sceneRoot);

        LayoutMetrics metrics = buildLayoutMetrics(previewData);

        sceneRoot.attachChild(createFloor(metrics));
        sceneRoot.attachChild(createCeiling(metrics));
        attachWalls(metrics);
        attachArchitecturalDetails(metrics);
        attachTeachingArea(metrics);
        attachSeats(metrics);
        attachCeilingLights(metrics);
        attachLights();
        positionCamera(metrics);

        titleText.setText(previewData.roomName());
        summaryText.setText(buildDefaultSummary());
        interactionHintText.setText(
            "Clic droit sur une place pour ses details | Maintenez clic gauche pour orienter la camera | ZQSD/WASD pour vous deplacer"
        );
        updateHudPositions();
    }

    private void initialiseHud() {
        titleText = new BitmapText(hudFont);
        titleText.setSize(hudFont.getCharSet().getRenderedSize() * 1.45f);
        titleText.setColor(ColorRGBA.Black);

        summaryText = new BitmapText(hudFont);
        summaryText.setSize(hudFont.getCharSet().getRenderedSize());
        summaryText.setColor(new ColorRGBA(0.12f, 0.18f, 0.28f, 1f));

        interactionHintText = new BitmapText(hudFont);
        interactionHintText.setSize(hudFont.getCharSet().getRenderedSize() * 0.9f);
        interactionHintText.setColor(new ColorRGBA(0.28f, 0.34f, 0.44f, 1f));

        guiNode.attachChild(titleText);
        guiNode.attachChild(summaryText);
        guiNode.attachChild(interactionHintText);
    }

    private void updateHudPositions() {
        if (titleText == null || summaryText == null || interactionHintText == null) {
            return;
        }

        float topMargin = cam.getHeight() - 18f;
        titleText.setLocalTranslation(18f, topMargin, 0f);
        summaryText.setLocalTranslation(18f, topMargin - titleText.getLineHeight() - 8f, 0f);
        interactionHintText.setLocalTranslation(18f, 24f + interactionHintText.getLineHeight(), 0f);
    }

    private void initialiseMaterials() {
        floorMaterial = createLitMaterial(new ColorRGBA(0.67f, 0.71f, 0.76f, 1f));
        wallMaterial = createLitMaterial(new ColorRGBA(0.88f, 0.91f, 0.95f, 1f));
        ceilingMaterial = createLitMaterial(new ColorRGBA(0.95f, 0.96f, 0.98f, 1f));
        deskMaterial = createLitMaterial(new ColorRGBA(0.46f, 0.31f, 0.2f, 1f));
        stageMaterial = createLitMaterial(new ColorRGBA(0.54f, 0.57f, 0.63f, 1f));
        trimMaterial = createLitMaterial(new ColorRGBA(0.23f, 0.34f, 0.47f, 1f));
        metalMaterial = createLitMaterial(new ColorRGBA(0.42f, 0.46f, 0.53f, 1f));
        glassMaterial = createLitMaterial(new ColorRGBA(0.55f, 0.73f, 0.89f, 1f));
        lightPanelMaterial = createFlatMaterial(new ColorRGBA(1f, 0.98f, 0.9f, 1f));
        screenMaterial = createFlatMaterial(new ColorRGBA(0.19f, 0.25f, 0.33f, 1f));
        availableSeatMaterial = createLitMaterial(new ColorRGBA(0.28f, 0.68f, 0.44f, 1f));
        reservedSeatMaterial = createLitMaterial(new ColorRGBA(0.88f, 0.47f, 0.25f, 1f));
        maintenanceSeatMaterial = createLitMaterial(new ColorRGBA(0.83f, 0.68f, 0.23f, 1f));
        unavailableSeatMaterial = createLitMaterial(new ColorRGBA(0.53f, 0.56f, 0.62f, 1f));
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

    private Material createFlatMaterial(ColorRGBA color) {
        Material material = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        material.setColor("Color", color);
        return material;
    }

    private Geometry createFloor(LayoutMetrics metrics) {
        Geometry floor = new Geometry(
            "room-floor",
            new Box(metrics.roomWidth() / 2f, FLOOR_THICKNESS, metrics.roomDepth() / 2f)
        );
        floor.setMaterial(floorMaterial);
        floor.setShadowMode(RenderQueue.ShadowMode.Receive);
        floor.setLocalTranslation(0f, -FLOOR_THICKNESS, 0f);
        return floor;
    }

    private Geometry createCeiling(LayoutMetrics metrics) {
        float ceilingDepth = metrics.roomDepth() * 0.68f;
        float ceilingCenterZ = -(metrics.roomDepth() - ceilingDepth) / 2f;
        Geometry ceiling = new Geometry(
            "room-ceiling-cutaway",
            new Box(metrics.roomWidth() / 2f, CEILING_THICKNESS, ceilingDepth / 2f)
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
            "back-accent-strip",
            metrics.roomWidth() * 0.42f,
            0.12f,
            0.04f,
            trimMaterial,
            new Vector3f(0f, 1.3f, -halfRoomDepth + 0.06f)
        ));

        float[] windowZPositions = {-metrics.roomDepth() * 0.16f, metrics.roomDepth() * 0.12f};
        for (int index = 0; index < windowZPositions.length; index++) {
            float windowZ = windowZPositions[index];
            String leftKey = "left-window-" + index;
            String rightKey = "right-window-" + index;

            sceneRoot.attachChild(createSimpleBox(
                leftKey,
                0.025f,
                0.78f,
                0.72f,
                glassMaterial,
                new Vector3f(-halfRoomWidth + 0.04f, 2.05f, windowZ)
            ));
            sceneRoot.attachChild(createSimpleBox(
                leftKey + "-frame",
                0.035f,
                0.86f,
                0.82f,
                trimMaterial,
                new Vector3f(-halfRoomWidth + 0.02f, 2.05f, windowZ)
            ));
            sceneRoot.attachChild(createSimpleBox(
                rightKey,
                0.025f,
                0.78f,
                0.72f,
                glassMaterial,
                new Vector3f(halfRoomWidth - 0.04f, 2.05f, windowZ)
            ));
            sceneRoot.attachChild(createSimpleBox(
                rightKey + "-frame",
                0.035f,
                0.86f,
                0.82f,
                trimMaterial,
                new Vector3f(halfRoomWidth - 0.02f, 2.05f, windowZ)
            ));
        }

        sceneRoot.attachChild(createSimpleBox(
            "door",
            0.52f,
            1.02f,
            0.03f,
            deskMaterial,
            new Vector3f(-halfRoomWidth + 1.05f, 1.02f, -halfRoomDepth + 0.07f)
        ));
        sceneRoot.attachChild(createSimpleBox(
            "door-handle",
            0.03f,
            0.03f,
            0.015f,
            metalMaterial,
            new Vector3f(-halfRoomWidth + 0.74f, 0.95f, -halfRoomDepth + 0.11f)
        ));

        sceneRoot.attachChild(createSimpleBox(
            "front-border-trim",
            metrics.roomWidth(),
            0.03f,
            0.05f,
            trimMaterial,
            new Vector3f(0f, FRONT_BORDER_HEIGHT + frameOffset, halfRoomDepth)
        ));
    }

    private Geometry createWall(String name, float width, float height, float depth, Vector3f position) {
        Geometry wall = new Geometry(name, new Box(width / 2f, height / 2f, depth / 2f));
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
            0.09f,
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
            Node desk = createTableNode("teacher-desk", 2.2f, 0.82f, 0.77f, deskMaterial);
            desk.setLocalTranslation(0f, 0f, frontZoneZ);
            desk.attachChild(createSimpleBox(
                "teacher-laptop",
                0.24f,
                0.015f,
                0.18f,
                screenMaterial,
                new Vector3f(0f, 0.81f, -0.05f)
            ));
            sceneRoot.attachChild(desk);
        }
    }

    private void attachSeats(LayoutMetrics metrics) {
        String disposition = normalize(previewData.disposition());

        for (Room3DPreviewData.SeatPreview seat : previewData.seats()) {
            float x = ((seat.column() - 1) * metrics.seatSpacingX()) - (metrics.layoutWidth() / 2f);
            float z = metrics.frontSeatZ() - ((seat.row() - 1) * metrics.seatSpacingZ());

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
            0.34f,
            0.09f,
            0.33f,
            seatMaterial,
            new Vector3f(0f, 0.35f, 0f)
        ));
        seatNode.attachChild(createSimpleBox(
            "seat-back-" + seat.number(),
            0.34f,
            0.34f,
            0.07f,
            seatMaterial,
            new Vector3f(0f, 0.77f, -0.26f)
        ));
        seatNode.attachChild(createSimpleBox(
            "seat-support-" + seat.number(),
            0.08f,
            0.15f,
            0.08f,
            metalMaterial,
            new Vector3f(0f, 0.15f, 0f)
        ));
        seatNode.attachChild(createChairLeg("seat-leg-fl-" + seat.number(), 0.24f, -0.2f));
        seatNode.attachChild(createChairLeg("seat-leg-fr-" + seat.number(), -0.24f, -0.2f));
        seatNode.attachChild(createChairLeg("seat-leg-bl-" + seat.number(), 0.24f, 0.2f));
        seatNode.attachChild(createChairLeg("seat-leg-br-" + seat.number(), -0.24f, 0.2f));

        if (!"reunion".equals(disposition)) {
            Node deskNode = createSeatDeskNode(seat.number(), disposition);
            deskNode.setLocalTranslation(0f, 0f, 0.66f);
            seatNode.attachChild(deskNode);
        }

        return seatNode;
    }

    private Geometry createChairLeg(String name, float x, float z) {
        return createSimpleBox(name, 0.03f, 0.18f, 0.03f, metalMaterial, new Vector3f(x, 0.17f, z));
    }

    private Node createSeatDeskNode(int seatNumber, String disposition) {
        Node deskNode;
        if ("informatique".equals(disposition)) {
            deskNode = createTableNode("lab-desk-" + seatNumber, 0.76f, 0.48f, 0.73f, deskMaterial);
            deskNode.attachChild(createSimpleBox(
                "monitor-" + seatNumber,
                0.19f,
                0.13f,
                0.025f,
                screenMaterial,
                new Vector3f(0f, 0.96f, 0.1f)
            ));
            deskNode.attachChild(createSimpleBox(
                "monitor-stand-" + seatNumber,
                0.025f,
                0.08f,
                0.025f,
                metalMaterial,
                new Vector3f(0f, 0.84f, 0.12f)
            ));
            deskNode.attachChild(createSimpleBox(
                "keyboard-" + seatNumber,
                0.18f,
                0.012f,
                0.07f,
                metalMaterial,
                new Vector3f(0f, 0.79f, -0.06f)
            ));
            return deskNode;
        }

        if ("conference".equals(disposition) || "u".equals(disposition)) {
            deskNode = createTableNode("conference-desk-" + seatNumber, 0.66f, 0.44f, 0.73f, deskMaterial);
            deskNode.attachChild(createSimpleBox(
                "conference-pad-" + seatNumber,
                0.15f,
                0.01f,
                0.1f,
                trimMaterial,
                new Vector3f(0f, 0.79f, 0.02f)
            ));
            return deskNode;
        }

        deskNode = createTableNode("class-desk-" + seatNumber, 0.54f, 0.36f, 0.71f, deskMaterial);
        deskNode.attachChild(createSimpleBox(
            "class-book-" + seatNumber,
            0.14f,
            0.025f,
            0.1f,
            trimMaterial,
            new Vector3f(0f, 0.76f, 0.02f)
        ));
        return deskNode;
    }

    private Node createTableNode(String name, float width, float depth, float topHeight, Material topMaterial) {
        Node tableNode = new Node(name);
        float topHalfHeight = 0.03f;
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
            0.03f,
            legHalfHeight,
            0.03f,
            metalMaterial,
            new Vector3f(legInsetX, legCenterY, legInsetZ)
        ));
        tableNode.attachChild(createSimpleBox(
            name + "-leg-2",
            0.03f,
            legHalfHeight,
            0.03f,
            metalMaterial,
            new Vector3f(-legInsetX, legCenterY, legInsetZ)
        ));
        tableNode.attachChild(createSimpleBox(
            name + "-leg-3",
            0.03f,
            legHalfHeight,
            0.03f,
            metalMaterial,
            new Vector3f(legInsetX, legCenterY, -legInsetZ)
        ));
        tableNode.attachChild(createSimpleBox(
            name + "-leg-4",
            0.03f,
            legHalfHeight,
            0.03f,
            metalMaterial,
            new Vector3f(-legInsetX, legCenterY, -legInsetZ)
        ));
        return tableNode;
    }

    private Node createPresentationScreen() {
        Node screenNode = new Node("presentation-screen");
        screenNode.attachChild(createSimpleBox(
            "screen-frame",
            1.78f,
            0.96f,
            0.06f,
            trimMaterial,
            new Vector3f(0f, 2.1f, 0f)
        ));
        screenNode.attachChild(createSimpleBox(
            "screen-surface",
            1.62f,
            0.82f,
            0.03f,
            screenMaterial,
            new Vector3f(0f, 2.1f, 0.01f)
        ));
        screenNode.attachChild(createSimpleBox(
            "screen-support-left",
            0.04f,
            0.82f,
            0.04f,
            metalMaterial,
            new Vector3f(-0.68f, 0.82f, 0f)
        ));
        screenNode.attachChild(createSimpleBox(
            "screen-support-right",
            0.04f,
            0.82f,
            0.04f,
            metalMaterial,
            new Vector3f(0.68f, 0.82f, 0f)
        ));
        screenNode.attachChild(createSimpleBox(
            "screen-base",
            0.84f,
            0.04f,
            0.28f,
            metalMaterial,
            new Vector3f(0f, 0.04f, 0.1f)
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

        for (int row = 0; row < zPositions.length; row++) {
            for (int column = 0; column < xPositions.length; column++) {
                float x = xPositions[column];
                float z = zPositions[row];
                sceneRoot.attachChild(createSimpleBox(
                    "ceiling-light-" + row + "-" + column,
                    0.5f,
                    0.02f,
                    0.82f,
                    lightPanelMaterial,
                    new Vector3f(x, WALL_HEIGHT - 0.12f, z)
                ));

                PointLight pointLight = new PointLight();
                pointLight.setColor(new ColorRGBA(1f, 0.96f, 0.9f, 1f).mult(0.95f));
                pointLight.setRadius(Math.max(8f, metrics.roomWidth()));
                pointLight.setPosition(new Vector3f(x, WALL_HEIGHT - 0.32f, z));
                sceneRoot.addLight(pointLight);
            }
        }
    }

    private void attachLights() {
        AmbientLight ambientLight = new AmbientLight();
        ambientLight.setColor(ColorRGBA.White.mult(0.72f));
        sceneRoot.addLight(ambientLight);

        DirectionalLight keyLight = new DirectionalLight();
        keyLight.setDirection(new Vector3f(-0.38f, -1f, -0.16f).normalizeLocal());
        keyLight.setColor(ColorRGBA.White.mult(0.72f));
        sceneRoot.addLight(keyLight);

        DirectionalLight fillLight = new DirectionalLight();
        fillLight.setDirection(new Vector3f(0.34f, -0.78f, 0.26f).normalizeLocal());
        fillLight.setColor(ColorRGBA.White.mult(0.24f));
        sceneRoot.addLight(fillLight);
    }

    private void positionCamera(LayoutMetrics metrics) {
        float maxDimension = Math.max(metrics.roomWidth(), metrics.roomDepth());
        float halfRoomDepth = metrics.roomDepth() / 2f;
        float screenFocusZ = Math.min(halfRoomDepth - 0.85f, metrics.frontSeatZ() + 2.35f);
        cam.setLocation(new Vector3f(
            metrics.roomWidth() * 0.08f,
            Math.min(1.9f, Math.max(1.65f, maxDimension * 0.16f)),
            Math.max(-halfRoomDepth + 1.25f, -metrics.roomDepth() * 0.34f)
        ));
        cam.lookAt(new Vector3f(0f, 1.75f, screenFocusZ), Vector3f.UNIT_Y);
        cam.setFrustumFar(Math.max(250f, maxDimension * 12f));
        flyCam.setMoveSpeed(Math.max(10f, maxDimension * 1.2f));
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

        float layoutWidth = Math.max(2.6f, Math.max(0, previewData.maxColumn() - 1) * seatSpacingX);
        float layoutDepth = Math.max(2.6f, Math.max(0, previewData.maxRow() - 1) * seatSpacingZ);
        float roomWidth = layoutWidth + 5.2f;
        float roomDepth = layoutDepth + 6.6f;
        float frontSeatZ = layoutDepth / 2f;

        return new LayoutMetrics(seatSpacingX, seatSpacingZ, layoutWidth, layoutDepth, roomWidth, roomDepth, frontSeatZ);
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

    private Geometry createSimpleBox(String name, float halfWidth, float halfHeight, float halfDepth,
                                     Material material, Vector3f position) {
        Geometry geometry = new Geometry(name, new Box(halfWidth, halfHeight, halfDepth));
        geometry.setMaterial(material);
        geometry.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        geometry.setLocalTranslation(position);
        return geometry;
    }

    private String resolveUserData(Spatial spatial, String key) {
        Spatial current = spatial;
        while (current != null) {
            String value = current.getUserData(key);
            if (value != null) {
                return value;
            }
            current = current.getParent();
        }
        return null;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }

    private record LayoutMetrics(
        float seatSpacingX,
        float seatSpacingZ,
        float layoutWidth,
        float layoutDepth,
        float roomWidth,
        float roomDepth,
        float frontSeatZ
    ) {
    }
}
