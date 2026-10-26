package com.example.skyblocks.client;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Blocky (Minecraft-style) models of the aquariums' own fish and sea creatures, for the vanilla aquariums: written by
 * tools/make_aqua_models.py (with their textures, textures/entity/aqua/), so change them there.
 */
final class AquaModels {
    /** Every one, and how big to draw it (its length in the aquariums over its length in the model). */
    static final String[] NAMES = {"clownfish", "blue_tang", "yellow_tang", "neon_tetra", "angelfish", "goldfish", "betta", "guppy", "discus", "shark", "tuna", "manta_ray", "grouper", "jellyfish", "sardine", "ocean_sunfish", "basking_shark", "hermit_crab", "crab", "stingray", "octopus", "lobster", "shrimp"};
    static final float[] SCALES = {0.906667F, 0.906667F, 0.896F, 0.64F, 0.96F, 0.738462F, 0.594286F, 0.576F, 0.693333F, 1.6F, 1.44F, 1.14286F, 1.6F, 1.33333F, 0.768F, 1.77778F, 1.90476F, 0.685714F, 1.088F, 1F, 1F, 0.457143F, 0.251429F};
    /** How each gets about (swim, drift, glide near the bottom, crawl on it), and how far it reaches from its middle (blocks). */
    static final String[] MOVES = {"swim", "swim", "swim", "swim", "swim", "swim", "swim", "swim", "swim", "swim", "swim", "swim", "swim", "drift", "swim", "swim", "swim", "crawl", "crawl", "glide", "crawl", "crawl", "crawl"};
    static final float[] REACH = {0.17F, 0.17F, 0.14F, 0.1F, 0.12F, 0.15F, 0.13F, 0.09F, 0.13F, 0.9F, 0.45F, 0.5F, 0.55F, 0.25F, 0.12F, 0.5F, 1.25F, 0.15F, 0.17F, 0.5F, 0.25F, 0.3F, 0.11F};

    private AquaModels() {}

    static ModelPart build(String name) {
        return switch (name) {
            case "clownfish" -> clownfish();
            case "blue_tang" -> blueTang();
            case "yellow_tang" -> yellowTang();
            case "neon_tetra" -> neonTetra();
            case "angelfish" -> angelfish();
            case "goldfish" -> goldfish();
            case "betta" -> betta();
            case "guppy" -> guppy();
            case "discus" -> discus();
            case "shark" -> shark();
            case "tuna" -> tuna();
            case "manta_ray" -> mantaRay();
            case "grouper" -> grouper();
            case "jellyfish" -> jellyfish();
            case "sardine" -> sardine();
            case "ocean_sunfish" -> oceanSunfish();
            case "basking_shark" -> baskingShark();
            case "hermit_crab" -> hermitCrab();
            case "crab" -> crab();
            case "stingray" -> stingray();
            case "octopus" -> octopus();
            case "lobster" -> lobster();
            case "shrimp" -> shrimp();
            default -> throw new IllegalArgumentException(name);
        };
    }

    private static void part(java.util.Map<String, PartDefinition> parts, String name, String parent, CubeListBuilder cubes, PartPose pose) {
        parts.put(name, parts.get(parent).addOrReplaceChild(name, cubes, pose));
    }

    /** clownfish: 0.34 blocks long, 6.0 px in the model. */
    static ModelPart clownfish() {
        MeshDefinition mesh = new MeshDefinition();
        java.util.Map<String, PartDefinition> parts = new java.util.HashMap<>();
        parts.put("", mesh.getRoot());
        part(parts, "body", "", CubeListBuilder.create().texOffs(0, 0).addBox(-1F, -1.5F, -3F, 2F, 3F, 6F).texOffs(22, 0).addBox(0F, -2.5F, -2F, 0F, 1F, 4F).texOffs(30, 0).addBox(0F, 1.5F, 0F, 0F, 1F, 2F), PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        part(parts, "tail", "body", CubeListBuilder.create().texOffs(16, 0).addBox(0F, -1.5F, 0F, 0F, 3F, 3F), PartPose.offsetAndRotation(0F, 0F, 3F, 0F, 0F, 0F));
        part(parts, "right_fin", "body", CubeListBuilder.create().texOffs(34, 0).addBox(0F, -0.5F, 0F, 0F, 1F, 2F), PartPose.offsetAndRotation(-1F, 0F, -1.5F, 0F, -0.5F, 0F));
        part(parts, "left_fin", "body", CubeListBuilder.create().texOffs(38, 0).addBox(0F, -0.5F, 0F, 0F, 1F, 2F), PartPose.offsetAndRotation(1F, 0F, -1.5F, 0F, 0.5F, 0F));
        return LayerDefinition.create(mesh, 64, 32).bakeRoot();
    }

    /** blue_tang: 0.34 blocks long, 6.0 px in the model. */
    static ModelPart blueTang() {
        MeshDefinition mesh = new MeshDefinition();
        java.util.Map<String, PartDefinition> parts = new java.util.HashMap<>();
        parts.put("", mesh.getRoot());
        part(parts, "body", "", CubeListBuilder.create().texOffs(0, 0).addBox(-1F, -2F, -3F, 2F, 4F, 6F).texOffs(22, 0).addBox(0F, -3F, -3F, 0F, 1F, 5F).texOffs(32, 0).addBox(0F, 2F, -2F, 0F, 1F, 4F), PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        part(parts, "tail", "body", CubeListBuilder.create().texOffs(16, 0).addBox(0F, -2F, 0F, 0F, 4F, 3F), PartPose.offsetAndRotation(0F, 0F, 3F, 0F, 0F, 0F));
        part(parts, "right_fin", "body", CubeListBuilder.create().texOffs(40, 0).addBox(0F, -0.5F, 0F, 0F, 1F, 2F), PartPose.offsetAndRotation(-1F, 0F, -1.5F, 0F, -0.5F, 0F));
        part(parts, "left_fin", "body", CubeListBuilder.create().texOffs(44, 0).addBox(0F, -0.5F, 0F, 0F, 1F, 2F), PartPose.offsetAndRotation(1F, 0F, -1.5F, 0F, 0.5F, 0F));
        return LayerDefinition.create(mesh, 64, 32).bakeRoot();
    }

    /** yellow_tang: 0.28 blocks long, 5.0 px in the model. */
    static ModelPart yellowTang() {
        MeshDefinition mesh = new MeshDefinition();
        java.util.Map<String, PartDefinition> parts = new java.util.HashMap<>();
        parts.put("", mesh.getRoot());
        part(parts, "body", "", CubeListBuilder.create().texOffs(0, 0).addBox(-0.5F, -2.5F, -2.5F, 1F, 5F, 5F).texOffs(12, 0).addBox(0F, -4.5F, -2.5F, 0F, 2F, 4F).texOffs(24, 0).addBox(0F, 2.5F, -1.5F, 0F, 2F, 3F), PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        part(parts, "tail", "body", CubeListBuilder.create().texOffs(20, 0).addBox(0F, -1.5F, 0F, 0F, 3F, 2F), PartPose.offsetAndRotation(0F, 0F, 2.5F, 0F, 0F, 0F));
        part(parts, "right_fin", "body", CubeListBuilder.create().texOffs(30, 0).addBox(0F, -0.5F, 0F, 0F, 1F, 2F), PartPose.offsetAndRotation(-0.5F, 0F, -1F, 0F, -0.5F, 0F));
        part(parts, "left_fin", "body", CubeListBuilder.create().texOffs(34, 0).addBox(0F, -0.5F, 0F, 0F, 1F, 2F), PartPose.offsetAndRotation(0.5F, 0F, -1F, 0F, 0.5F, 0F));
        return LayerDefinition.create(mesh, 64, 32).bakeRoot();
    }

    /** neon_tetra: 0.2 blocks long, 5.0 px in the model. */
    static ModelPart neonTetra() {
        MeshDefinition mesh = new MeshDefinition();
        java.util.Map<String, PartDefinition> parts = new java.util.HashMap<>();
        parts.put("", mesh.getRoot());
        part(parts, "body", "", CubeListBuilder.create().texOffs(0, 0).addBox(-0.5F, -1F, -2.5F, 1F, 2F, 5F).texOffs(16, 0).addBox(0F, -2F, -0.5F, 0F, 1F, 1F), PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        part(parts, "tail", "body", CubeListBuilder.create().texOffs(12, 0).addBox(0F, -1F, 0F, 0F, 2F, 2F), PartPose.offsetAndRotation(0F, 0F, 2.5F, 0F, 0F, 0F));
        return LayerDefinition.create(mesh, 64, 32).bakeRoot();
    }

    /** angelfish: 0.24 blocks long, 4.0 px in the model. */
    static ModelPart angelfish() {
        MeshDefinition mesh = new MeshDefinition();
        java.util.Map<String, PartDefinition> parts = new java.util.HashMap<>();
        parts.put("", mesh.getRoot());
        part(parts, "body", "", CubeListBuilder.create().texOffs(0, 0).addBox(-0.5F, -2F, -2F, 1F, 4F, 4F).texOffs(10, 0).addBox(0F, -6F, -1F, 0F, 4F, 3F).texOffs(16, 0).addBox(0F, 2F, -1F, 0F, 4F, 3F), PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        part(parts, "tail", "body", CubeListBuilder.create().texOffs(22, 0).addBox(0F, -1.5F, 0F, 0F, 3F, 2F), PartPose.offsetAndRotation(0F, 0F, 2F, 0F, 0F, 0F));
        part(parts, "right_fin", "body", CubeListBuilder.create().texOffs(26, 0).addBox(0F, -0.5F, 0F, 0F, 1F, 2F), PartPose.offsetAndRotation(-0.5F, 0F, -0.5F, 0F, -0.5F, 0F));
        part(parts, "left_fin", "body", CubeListBuilder.create().texOffs(30, 0).addBox(0F, -0.5F, 0F, 0F, 1F, 2F), PartPose.offsetAndRotation(0.5F, 0F, -0.5F, 0F, 0.5F, 0F));
        return LayerDefinition.create(mesh, 64, 32).bakeRoot();
    }

    /** goldfish: 0.3 blocks long, 6.5 px in the model. */
    static ModelPart goldfish() {
        MeshDefinition mesh = new MeshDefinition();
        java.util.Map<String, PartDefinition> parts = new java.util.HashMap<>();
        parts.put("", mesh.getRoot());
        part(parts, "body", "", CubeListBuilder.create().texOffs(0, 0).addBox(-1.5F, -1.5F, -2.5F, 3F, 3F, 5F).texOffs(24, 0).addBox(0F, -2.5F, -1.5F, 0F, 1F, 3F), PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        part(parts, "tail", "body", CubeListBuilder.create().texOffs(16, 0).addBox(0F, -2F, 0F, 0F, 4F, 4F), PartPose.offsetAndRotation(0F, 0F, 2.5F, 0F, 0F, 0F));
        part(parts, "right_fin", "body", CubeListBuilder.create().texOffs(30, 0).addBox(0F, -0.5F, 0F, 0F, 1F, 2F), PartPose.offsetAndRotation(-1.5F, 0F, -1F, 0F, -0.5F, 0F));
        part(parts, "left_fin", "body", CubeListBuilder.create().texOffs(34, 0).addBox(0F, -0.5F, 0F, 0F, 1F, 2F), PartPose.offsetAndRotation(1.5F, 0F, -1F, 0F, 0.5F, 0F));
        return LayerDefinition.create(mesh, 64, 32).bakeRoot();
    }

    /** betta: 0.26 blocks long, 7.0 px in the model. */
    static ModelPart betta() {
        MeshDefinition mesh = new MeshDefinition();
        java.util.Map<String, PartDefinition> parts = new java.util.HashMap<>();
        parts.put("", mesh.getRoot());
        part(parts, "body", "", CubeListBuilder.create().texOffs(18, 0).addBox(-0.5F, -1F, -2F, 1F, 2F, 4F).texOffs(28, 0).addBox(0F, -4F, -1F, 0F, 3F, 3F).texOffs(10, 0).addBox(0F, 1F, -2F, 0F, 4F, 4F), PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        part(parts, "tail", "body", CubeListBuilder.create().texOffs(0, 0).addBox(0F, -3F, 0F, 0F, 6F, 5F), PartPose.offsetAndRotation(0F, 0F, 2F, 0F, 0F, 0F));
        part(parts, "right_fin", "body", CubeListBuilder.create().texOffs(34, 0).addBox(0F, -0.5F, 0F, 0F, 1F, 2F), PartPose.offsetAndRotation(-0.5F, 0F, -0.5F, 0F, -0.5F, 0F));
        part(parts, "left_fin", "body", CubeListBuilder.create().texOffs(38, 0).addBox(0F, -0.5F, 0F, 0F, 1F, 2F), PartPose.offsetAndRotation(0.5F, 0F, -0.5F, 0F, 0.5F, 0F));
        return LayerDefinition.create(mesh, 64, 32).bakeRoot();
    }

    /** guppy: 0.18 blocks long, 5.0 px in the model. */
    static ModelPart guppy() {
        MeshDefinition mesh = new MeshDefinition();
        java.util.Map<String, PartDefinition> parts = new java.util.HashMap<>();
        parts.put("", mesh.getRoot());
        part(parts, "body", "", CubeListBuilder.create().texOffs(6, 0).addBox(-0.5F, -1F, -2F, 1F, 2F, 4F).texOffs(16, 0).addBox(0F, -2F, -1F, 0F, 1F, 2F), PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        part(parts, "tail", "body", CubeListBuilder.create().texOffs(0, 0).addBox(0F, -2F, 0F, 0F, 4F, 3F), PartPose.offsetAndRotation(0F, 0F, 2F, 0F, 0F, 0F));
        return LayerDefinition.create(mesh, 64, 32).bakeRoot();
    }

    /** discus: 0.26 blocks long, 6.0 px in the model. */
    static ModelPart discus() {
        MeshDefinition mesh = new MeshDefinition();
        java.util.Map<String, PartDefinition> parts = new java.util.HashMap<>();
        parts.put("", mesh.getRoot());
        part(parts, "body", "", CubeListBuilder.create().texOffs(0, 0).addBox(-0.5F, -3F, -3F, 1F, 6F, 6F).texOffs(14, 0).addBox(0F, -4F, -3F, 0F, 1F, 5F).texOffs(24, 0).addBox(0F, 3F, -3F, 0F, 1F, 5F), PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        part(parts, "tail", "body", CubeListBuilder.create().texOffs(34, 0).addBox(0F, -1.5F, 0F, 0F, 3F, 2F), PartPose.offsetAndRotation(0F, 0F, 3F, 0F, 0F, 0F));
        part(parts, "right_fin", "body", CubeListBuilder.create().texOffs(38, 0).addBox(0F, -0.5F, 0F, 0F, 1F, 2F), PartPose.offsetAndRotation(-0.5F, 0F, -1.5F, 0F, -0.5F, 0F));
        part(parts, "left_fin", "body", CubeListBuilder.create().texOffs(42, 0).addBox(0F, -0.5F, 0F, 0F, 1F, 2F), PartPose.offsetAndRotation(0.5F, 0F, -1.5F, 0F, 0.5F, 0F));
        return LayerDefinition.create(mesh, 64, 32).bakeRoot();
    }

    /** shark: 1.8 blocks long, 18 px in the model. */
    static ModelPart shark() {
        MeshDefinition mesh = new MeshDefinition();
        java.util.Map<String, PartDefinition> parts = new java.util.HashMap<>();
        parts.put("", mesh.getRoot());
        part(parts, "body", "", CubeListBuilder.create().texOffs(0, 0).addBox(-3F, -3F, -8F, 6F, 6F, 14F).texOffs(18, 20).addBox(-2.5F, -2F, -12F, 5F, 4F, 4F).texOffs(48, 0).addBox(0F, -8F, -3F, 0F, 5F, 5F), PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        part(parts, "tail", "body", CubeListBuilder.create().texOffs(0, 20).addBox(-1.5F, -1.5F, 0F, 3F, 3F, 6F), PartPose.offsetAndRotation(0F, -0.5F, 6F, 0F, 0F, 0F));
        part(parts, "tail_fin", "tail", CubeListBuilder.create().texOffs(40, 0).addBox(0F, -7F, 0F, 0F, 9F, 4F), PartPose.offsetAndRotation(0F, 0F, 5F, 0.45F, 0F, 0F));
        part(parts, "right_fin", "body", CubeListBuilder.create().texOffs(36, 20).addBox(-6F, 0F, 0F, 6F, 0F, 4F), PartPose.offsetAndRotation(-3F, 2F, -3F, 0F, 0F, 0.35F));
        part(parts, "left_fin", "body", CubeListBuilder.create().texOffs(0, 29).addBox(0F, 0F, 0F, 6F, 0F, 4F), PartPose.offsetAndRotation(3F, 2F, -3F, 0F, 0F, -0.35F));
        return LayerDefinition.create(mesh, 64, 64).bakeRoot();
    }

    /** tuna: 0.9 blocks long, 10.0 px in the model. */
    static ModelPart tuna() {
        MeshDefinition mesh = new MeshDefinition();
        java.util.Map<String, PartDefinition> parts = new java.util.HashMap<>();
        parts.put("", mesh.getRoot());
        part(parts, "body", "", CubeListBuilder.create().texOffs(0, 0).addBox(-2F, -2.5F, -5F, 4F, 5F, 10F).texOffs(32, 0).addBox(0F, -5.5F, -3F, 0F, 3F, 3F).texOffs(0, 15).addBox(0F, -3.5F, 2F, 0F, 1F, 3F).texOffs(6, 15).addBox(0F, 2.5F, 2F, 0F, 1F, 3F), PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        part(parts, "tail", "body", CubeListBuilder.create().texOffs(38, 0).addBox(-1F, -1F, 0F, 2F, 2F, 3F), PartPose.offsetAndRotation(0F, 0F, 5F, 0F, 0F, 0F));
        part(parts, "tail_fin", "tail", CubeListBuilder.create().texOffs(28, 0).addBox(0F, -4F, 0F, 0F, 8F, 2F), PartPose.offsetAndRotation(0F, 0F, 3F, 0F, 0F, 0F));
        part(parts, "right_fin", "body", CubeListBuilder.create().texOffs(48, 0).addBox(0F, 0F, 0F, 0F, 1F, 3F), PartPose.offsetAndRotation(-2F, 0.5F, -2F, 0F, -0.6F, 0F));
        part(parts, "left_fin", "body", CubeListBuilder.create().texOffs(54, 0).addBox(0F, 0F, 0F, 0F, 1F, 3F), PartPose.offsetAndRotation(2F, 0.5F, -2F, 0F, 0.6F, 0F));
        return LayerDefinition.create(mesh, 64, 32).bakeRoot();
    }

    /** manta_ray: 1 blocks long, 14 px in the model. */
    static ModelPart mantaRay() {
        MeshDefinition mesh = new MeshDefinition();
        java.util.Map<String, PartDefinition> parts = new java.util.HashMap<>();
        parts.put("", mesh.getRoot());
        part(parts, "body", "", CubeListBuilder.create().texOffs(0, 0).addBox(-3F, -1F, -4F, 6F, 2F, 8F).texOffs(0, 17).addBox(-3F, -0.5F, -6F, 1F, 1F, 2F).texOffs(6, 17).addBox(2F, -0.5F, -6F, 1F, 1F, 2F), PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        part(parts, "right_wing", "body", CubeListBuilder.create().texOffs(0, 10).addBox(-9F, -0.5F, -3F, 9F, 1F, 6F), PartPose.offsetAndRotation(-3F, 0F, -1F, 0F, 0F, 0F));
        part(parts, "left_wing", "body", CubeListBuilder.create().texOffs(30, 10).addBox(0F, -0.5F, -3F, 9F, 1F, 6F), PartPose.offsetAndRotation(3F, 0F, -1F, 0F, 0F, 0F));
        part(parts, "tail", "body", CubeListBuilder.create().texOffs(28, 0).addBox(-0.5F, -0.5F, 0F, 1F, 1F, 8F), PartPose.offsetAndRotation(0F, 0F, 4F, 0F, 0F, 0F));
        return LayerDefinition.create(mesh, 64, 32).bakeRoot();
    }

    /** grouper: 1.1 blocks long, 11.0 px in the model. */
    static ModelPart grouper() {
        MeshDefinition mesh = new MeshDefinition();
        java.util.Map<String, PartDefinition> parts = new java.util.HashMap<>();
        parts.put("", mesh.getRoot());
        part(parts, "body", "", CubeListBuilder.create().texOffs(0, 0).addBox(-2.5F, -2.5F, -5.5F, 5F, 5F, 11F).texOffs(32, 0).addBox(0F, -4.5F, -3.5F, 0F, 2F, 7F).texOffs(52, 0).addBox(0F, 2.5F, 0.5F, 0F, 2F, 3F), PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        part(parts, "tail", "body", CubeListBuilder.create().texOffs(46, 0).addBox(0F, -2.5F, 0F, 0F, 5F, 3F), PartPose.offsetAndRotation(0F, 0F, 5.5F, 0F, 0F, 0F));
        part(parts, "right_fin", "body", CubeListBuilder.create().texOffs(58, 0).addBox(0F, -1F, 0F, 0F, 2F, 3F), PartPose.offsetAndRotation(-2.5F, 0F, -4F, 0F, -0.5F, 0F));
        part(parts, "left_fin", "body", CubeListBuilder.create().texOffs(0, 16).addBox(0F, -1F, 0F, 0F, 2F, 3F), PartPose.offsetAndRotation(2.5F, 0F, -4F, 0F, 0.5F, 0F));
        return LayerDefinition.create(mesh, 64, 32).bakeRoot();
    }

    /** jellyfish: 0.5 blocks long, 6 px in the model. */
    static ModelPart jellyfish() {
        MeshDefinition mesh = new MeshDefinition();
        java.util.Map<String, PartDefinition> parts = new java.util.HashMap<>();
        parts.put("", mesh.getRoot());
        part(parts, "body", "", CubeListBuilder.create().texOffs(0, 0).addBox(-3F, -6F, -3F, 6F, 4F, 6F).texOffs(0, 10).addBox(-2F, -7F, -2F, 4F, 1F, 4F).texOffs(34, 0).addBox(-2F, -2F, -2F, 4F, 2F, 4F), PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        part(parts, "tentacle0", "body", CubeListBuilder.create().texOffs(24, 0).addBox(0F, 0F, -0.5F, 0F, 9F, 1F), PartPose.offsetAndRotation(-2F, 0F, -2F, 0F, 0F, 0F));
        part(parts, "tentacle1", "body", CubeListBuilder.create().texOffs(26, 0).addBox(0F, 0F, -0.5F, 0F, 9F, 1F), PartPose.offsetAndRotation(2F, 0F, -2F, 0F, 0F, 0F));
        part(parts, "tentacle2", "body", CubeListBuilder.create().texOffs(28, 0).addBox(0F, 0F, -0.5F, 0F, 9F, 1F), PartPose.offsetAndRotation(-2F, 0F, 2F, 0F, 0F, 0F));
        part(parts, "tentacle3", "body", CubeListBuilder.create().texOffs(30, 0).addBox(0F, 0F, -0.5F, 0F, 9F, 1F), PartPose.offsetAndRotation(2F, 0F, 2F, 0F, 0F, 0F));
        part(parts, "tentacle4", "body", CubeListBuilder.create().texOffs(32, 0).addBox(0F, 0F, -0.5F, 0F, 6F, 1F), PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        return LayerDefinition.create(mesh, 64, 32).bakeRoot();
    }

    /** sardine: 0.24 blocks long, 5.0 px in the model. */
    static ModelPart sardine() {
        MeshDefinition mesh = new MeshDefinition();
        java.util.Map<String, PartDefinition> parts = new java.util.HashMap<>();
        parts.put("", mesh.getRoot());
        part(parts, "body", "", CubeListBuilder.create().texOffs(0, 0).addBox(-0.5F, -1F, -2.5F, 1F, 2F, 5F).texOffs(16, 0).addBox(0F, -2F, -0.5F, 0F, 1F, 1F), PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        part(parts, "tail", "body", CubeListBuilder.create().texOffs(12, 0).addBox(0F, -1F, 0F, 0F, 2F, 2F), PartPose.offsetAndRotation(0F, 0F, 2.5F, 0F, 0F, 0F));
        return LayerDefinition.create(mesh, 64, 32).bakeRoot();
    }

    /** ocean_sunfish: 1 blocks long, 9 px in the model. */
    static ModelPart oceanSunfish() {
        MeshDefinition mesh = new MeshDefinition();
        java.util.Map<String, PartDefinition> parts = new java.util.HashMap<>();
        parts.put("", mesh.getRoot());
        part(parts, "body", "", CubeListBuilder.create().texOffs(0, 0).addBox(-1.5F, -5F, -4F, 3F, 10F, 8F).texOffs(38, 0).addBox(-1F, -2F, -5F, 2F, 4F, 1F).texOffs(26, 0).addBox(0F, -12F, 1F, 0F, 7F, 3F).texOffs(32, 0).addBox(0F, 5F, 1F, 0F, 7F, 3F), PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        part(parts, "tail", "body", CubeListBuilder.create().texOffs(22, 0).addBox(0F, -5F, 0F, 0F, 10F, 2F), PartPose.offsetAndRotation(0F, 0F, 4F, 0F, 0F, 0F));
        part(parts, "right_fin", "body", CubeListBuilder.create().texOffs(44, 0).addBox(0F, -1F, 0F, 0F, 2F, 2F), PartPose.offsetAndRotation(-1.5F, -1F, -2F, 0F, 0F, 0F));
        part(parts, "left_fin", "body", CubeListBuilder.create().texOffs(48, 0).addBox(0F, -1F, 0F, 0F, 2F, 2F), PartPose.offsetAndRotation(1.5F, -1F, -2F, 0F, 0F, 0F));
        return LayerDefinition.create(mesh, 64, 32).bakeRoot();
    }

    /** basking_shark: 2.5 blocks long, 21 px in the model. */
    static ModelPart baskingShark() {
        MeshDefinition mesh = new MeshDefinition();
        java.util.Map<String, PartDefinition> parts = new java.util.HashMap<>();
        parts.put("", mesh.getRoot());
        part(parts, "body", "", CubeListBuilder.create().texOffs(0, 0).addBox(-4F, -4F, -8F, 8F, 8F, 16F).texOffs(12, 24).addBox(-3.5F, -3F, -13F, 7F, 6F, 5F).texOffs(0, 24).addBox(0F, -10F, -2F, 0F, 6F, 6F), PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        part(parts, "tail", "body", CubeListBuilder.create().texOffs(36, 24).addBox(-2F, -2F, 0F, 4F, 4F, 7F), PartPose.offsetAndRotation(0F, -0.5F, 8F, 0F, 0F, 0F));
        part(parts, "tail_fin", "tail", CubeListBuilder.create().texOffs(48, 0).addBox(0F, -9F, 0F, 0F, 11F, 5F), PartPose.offsetAndRotation(0F, 0F, 6F, 0.45F, 0F, 0F));
        part(parts, "right_fin", "body", CubeListBuilder.create().texOffs(0, 36).addBox(-7F, 0F, 0F, 7F, 0F, 5F), PartPose.offsetAndRotation(-4F, 3F, -3F, 0F, 0F, 0.35F));
        part(parts, "left_fin", "body", CubeListBuilder.create().texOffs(24, 36).addBox(0F, 0F, 0F, 7F, 0F, 5F), PartPose.offsetAndRotation(4F, 3F, -3F, 0F, 0F, -0.35F));
        return LayerDefinition.create(mesh, 64, 64).bakeRoot();
    }

    /** hermit_crab: 0.3 blocks long, 7 px in the model. */
    static ModelPart hermitCrab() {
        MeshDefinition mesh = new MeshDefinition();
        java.util.Map<String, PartDefinition> parts = new java.util.HashMap<>();
        parts.put("", mesh.getRoot());
        part(parts, "body", "", CubeListBuilder.create().texOffs(0, 0).addBox(-2.5F, -6F, -1F, 5F, 5F, 5F).texOffs(20, 0).addBox(-1.5F, -7F, 0F, 3F, 1F, 3F).texOffs(32, 0).addBox(-1.5F, -3F, -3F, 3F, 2F, 2F).texOffs(58, 0).addBox(-1.5F, -5F, -3F, 1F, 2F, 1F).texOffs(0, 10).addBox(0.5F, -5F, -3F, 1F, 2F, 1F), PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        part(parts, "right_claw", "body", CubeListBuilder.create().texOffs(42, 0).addBox(-1F, -1F, -2F, 2F, 2F, 2F), PartPose.offsetAndRotation(-1F, -1.5F, -3F, 0F, 0F, 0F));
        part(parts, "right_leg0", "body", CubeListBuilder.create().texOffs(4, 10).addBox(-0.5F, 0F, -0.5F, 1F, 2F, 1F), PartPose.offsetAndRotation(-2F, -1F, -1.5F, 0F, 0F, 0.6F));
        part(parts, "right_leg1", "body", CubeListBuilder.create().texOffs(8, 10).addBox(-0.5F, 0F, -0.5F, 1F, 2F, 1F), PartPose.offsetAndRotation(-2F, -1F, 0F, 0F, 0F, 0.6F));
        part(parts, "left_claw", "body", CubeListBuilder.create().texOffs(50, 0).addBox(-1F, -1F, -2F, 2F, 2F, 2F), PartPose.offsetAndRotation(1F, -1.5F, -3F, 0F, 0F, 0F));
        part(parts, "left_leg0", "body", CubeListBuilder.create().texOffs(12, 10).addBox(-0.5F, 0F, -0.5F, 1F, 2F, 1F), PartPose.offsetAndRotation(2F, -1F, -1.5F, 0F, 0F, -0.6F));
        part(parts, "left_leg1", "body", CubeListBuilder.create().texOffs(16, 10).addBox(-0.5F, 0F, -0.5F, 1F, 2F, 1F), PartPose.offsetAndRotation(2F, -1F, 0F, 0F, 0F, -0.6F));
        return LayerDefinition.create(mesh, 64, 32).bakeRoot();
    }

    /** crab: 0.34 blocks long, 5 px in the model. */
    static ModelPart crab() {
        MeshDefinition mesh = new MeshDefinition();
        java.util.Map<String, PartDefinition> parts = new java.util.HashMap<>();
        parts.put("", mesh.getRoot());
        part(parts, "body", "", CubeListBuilder.create().texOffs(0, 0).addBox(-3F, -4F, -2F, 6F, 2F, 4F).texOffs(0, 6).addBox(-1.5F, -5F, -2F, 1F, 1F, 1F).texOffs(4, 6).addBox(0.5F, -5F, -2F, 1F, 1F, 1F), PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        part(parts, "right_claw", "body", CubeListBuilder.create().texOffs(20, 0).addBox(-1F, -1F, -3F, 2F, 2F, 3F), PartPose.offsetAndRotation(-2.5F, -3F, -2F, 0F, 0F, 0F));
        part(parts, "right_leg0", "body", CubeListBuilder.create().texOffs(30, 0).addBox(-0.5F, 0F, -0.5F, 1F, 4F, 1F), PartPose.offsetAndRotation(-3F, -3F, -1F, 0F, 0F, 0.7F));
        part(parts, "right_leg1", "body", CubeListBuilder.create().texOffs(34, 0).addBox(-0.5F, 0F, -0.5F, 1F, 4F, 1F), PartPose.offsetAndRotation(-3F, -3F, 0.2F, 0F, 0F, 0.7F));
        part(parts, "right_leg2", "body", CubeListBuilder.create().texOffs(38, 0).addBox(-0.5F, 0F, -0.5F, 1F, 4F, 1F), PartPose.offsetAndRotation(-3F, -3F, 1.4F, 0F, 0F, 0.7F));
        part(parts, "left_claw", "body", CubeListBuilder.create().texOffs(42, 0).addBox(-1F, -1F, -3F, 2F, 2F, 3F), PartPose.offsetAndRotation(2.5F, -3F, -2F, 0F, 0F, 0F));
        part(parts, "left_leg0", "body", CubeListBuilder.create().texOffs(52, 0).addBox(-0.5F, 0F, -0.5F, 1F, 4F, 1F), PartPose.offsetAndRotation(3F, -3F, -1F, 0F, 0F, -0.7F));
        part(parts, "left_leg1", "body", CubeListBuilder.create().texOffs(56, 0).addBox(-0.5F, 0F, -0.5F, 1F, 4F, 1F), PartPose.offsetAndRotation(3F, -3F, 0.2F, 0F, 0F, -0.7F));
        part(parts, "left_leg2", "body", CubeListBuilder.create().texOffs(60, 0).addBox(-0.5F, 0F, -0.5F, 1F, 4F, 1F), PartPose.offsetAndRotation(3F, -3F, 1.4F, 0F, 0F, -0.7F));
        return LayerDefinition.create(mesh, 64, 32).bakeRoot();
    }

    /** stingray: 1 blocks long, 16 px in the model. */
    static ModelPart stingray() {
        MeshDefinition mesh = new MeshDefinition();
        java.util.Map<String, PartDefinition> parts = new java.util.HashMap<>();
        parts.put("", mesh.getRoot());
        part(parts, "body", "", CubeListBuilder.create().texOffs(24, 0).addBox(-3F, -1F, -5F, 6F, 1F, 9F), PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        part(parts, "right_wing", "body", CubeListBuilder.create().texOffs(0, 12).addBox(-4F, -1F, -4F, 4F, 1F, 7F), PartPose.offsetAndRotation(-3F, 0F, -0.5F, 0F, 0F, 0F));
        part(parts, "left_wing", "body", CubeListBuilder.create().texOffs(22, 12).addBox(0F, -1F, -4F, 4F, 1F, 7F), PartPose.offsetAndRotation(3F, 0F, -0.5F, 0F, 0F, 0F));
        part(parts, "tail", "body", CubeListBuilder.create().texOffs(0, 0).addBox(-0.5F, -0.5F, 0F, 1F, 1F, 11F), PartPose.offsetAndRotation(0F, -0.5F, 4F, 0F, 0F, 0F));
        return LayerDefinition.create(mesh, 64, 32).bakeRoot();
    }

    /** octopus: 0.5 blocks long, 8 px in the model. */
    static ModelPart octopus() {
        MeshDefinition mesh = new MeshDefinition();
        java.util.Map<String, PartDefinition> parts = new java.util.HashMap<>();
        parts.put("", mesh.getRoot());
        part(parts, "body", "", CubeListBuilder.create().texOffs(0, 0).addBox(-2.5F, -8F, -1F, 5F, 5F, 6F).texOffs(22, 0).addBox(-2F, -4F, -2F, 4F, 3F, 4F), PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        part(parts, "tentacle0", "body", CubeListBuilder.create().texOffs(38, 0).addBox(-0.5F, 0F, 0F, 1F, 1F, 6F), PartPose.offsetAndRotation(0F, -1F, 1.5F, 0F, 0F, 0F));
        part(parts, "tentacle1", "body", CubeListBuilder.create().texOffs(0, 11).addBox(-0.5F, 0F, 0F, 1F, 1F, 6F), PartPose.offsetAndRotation(1.06066F, -1F, 1.06066F, 0F, 0.785398F, 0F));
        part(parts, "tentacle2", "body", CubeListBuilder.create().texOffs(14, 11).addBox(-0.5F, 0F, 0F, 1F, 1F, 6F), PartPose.offsetAndRotation(1.5F, -1F, 9.18485e-17F, 0F, 1.5708F, 0F));
        part(parts, "tentacle3", "body", CubeListBuilder.create().texOffs(28, 11).addBox(-0.5F, 0F, 0F, 1F, 1F, 6F), PartPose.offsetAndRotation(1.06066F, -1F, -1.06066F, 0F, 2.35619F, 0F));
        part(parts, "tentacle4", "body", CubeListBuilder.create().texOffs(42, 11).addBox(-0.5F, 0F, 0F, 1F, 1F, 6F), PartPose.offsetAndRotation(1.83697e-16F, -1F, -1.5F, 0F, 3.14159F, 0F));
        part(parts, "tentacle5", "body", CubeListBuilder.create().texOffs(0, 18).addBox(-0.5F, 0F, 0F, 1F, 1F, 6F), PartPose.offsetAndRotation(-1.06066F, -1F, -1.06066F, 0F, 3.92699F, 0F));
        part(parts, "tentacle6", "body", CubeListBuilder.create().texOffs(14, 18).addBox(-0.5F, 0F, 0F, 1F, 1F, 6F), PartPose.offsetAndRotation(-1.5F, -1F, -2.75546e-16F, 0F, 4.71239F, 0F));
        part(parts, "tentacle7", "body", CubeListBuilder.create().texOffs(28, 18).addBox(-0.5F, 0F, 0F, 1F, 1F, 6F), PartPose.offsetAndRotation(-1.06066F, -1F, 1.06066F, 0F, 5.49779F, 0F));
        return LayerDefinition.create(mesh, 64, 32).bakeRoot();
    }

    /** lobster: 0.6 blocks long, 21 px in the model. */
    static ModelPart lobster() {
        MeshDefinition mesh = new MeshDefinition();
        java.util.Map<String, PartDefinition> parts = new java.util.HashMap<>();
        parts.put("", mesh.getRoot());
        part(parts, "body", "", CubeListBuilder.create().texOffs(0, 0).addBox(-1.5F, -4F, -4F, 3F, 3F, 5F).texOffs(20, 8).addBox(-1F, -4F, -6F, 2F, 2F, 2F).texOffs(16, 0).addBox(-1F, -4F, -14F, 0F, 0F, 8F).texOffs(32, 0).addBox(1F, -4F, -14F, 0F, 0F, 8F), PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        part(parts, "tail", "body", CubeListBuilder.create().texOffs(48, 0).addBox(-1F, -1.5F, 0F, 2F, 2F, 5F).texOffs(0, 13).addBox(-2F, -0.5F, 5F, 4F, 0F, 2F), PartPose.offsetAndRotation(0F, -2.5F, 1F, 0F, 0F, 0F));
        part(parts, "right_claw", "body", CubeListBuilder.create().texOffs(28, 8).addBox(-1F, -0.5F, -3F, 1F, 1F, 3F).texOffs(0, 8).addBox(-1.5F, -1F, -6F, 2F, 2F, 3F), PartPose.offsetAndRotation(-1.5F, -3F, -4F, 0F, -0.35F, 0F));
        part(parts, "right_leg0", "body", CubeListBuilder.create().texOffs(36, 8).addBox(-0.5F, 0F, 0F, 0F, 3F, 1F), PartPose.offsetAndRotation(-1.5F, -2F, -3F, 0F, 0F, 0.7F));
        part(parts, "right_leg1", "body", CubeListBuilder.create().texOffs(38, 8).addBox(-0.5F, 0F, 0F, 0F, 3F, 1F), PartPose.offsetAndRotation(-1.5F, -2F, -1.7F, 0F, 0F, 0.7F));
        part(parts, "right_leg2", "body", CubeListBuilder.create().texOffs(40, 8).addBox(-0.5F, 0F, 0F, 0F, 3F, 1F), PartPose.offsetAndRotation(-1.5F, -2F, -0.4F, 0F, 0F, 0.7F));
        part(parts, "left_claw", "body", CubeListBuilder.create().texOffs(42, 8).addBox(0F, -0.5F, -3F, 1F, 1F, 3F).texOffs(10, 8).addBox(-0.5F, -1F, -6F, 2F, 2F, 3F), PartPose.offsetAndRotation(1.5F, -3F, -4F, 0F, 0.35F, 0F));
        part(parts, "left_leg0", "body", CubeListBuilder.create().texOffs(50, 8).addBox(-0.5F, 0F, 0F, 0F, 3F, 1F), PartPose.offsetAndRotation(1.5F, -2F, -3F, 0F, 0F, -0.7F));
        part(parts, "left_leg1", "body", CubeListBuilder.create().texOffs(52, 8).addBox(-0.5F, 0F, 0F, 0F, 3F, 1F), PartPose.offsetAndRotation(1.5F, -2F, -1.7F, 0F, 0F, -0.7F));
        part(parts, "left_leg2", "body", CubeListBuilder.create().texOffs(54, 8).addBox(-0.5F, 0F, 0F, 0F, 3F, 1F), PartPose.offsetAndRotation(1.5F, -2F, -0.4F, 0F, 0F, -0.7F));
        return LayerDefinition.create(mesh, 64, 32).bakeRoot();
    }

    /** shrimp: 0.22 blocks long, 14 px in the model. */
    static ModelPart shrimp() {
        MeshDefinition mesh = new MeshDefinition();
        java.util.Map<String, PartDefinition> parts = new java.util.HashMap<>();
        parts.put("", mesh.getRoot());
        part(parts, "body", "", CubeListBuilder.create().texOffs(24, 0).addBox(-1F, -3F, -3F, 2F, 2F, 3F).texOffs(0, 0).addBox(-0.5F, -3F, -9F, 0F, 0F, 6F).texOffs(12, 0).addBox(0.5F, -3F, -9F, 0F, 0F, 6F).texOffs(52, 0).addBox(-0.5F, -1F, -2.5F, 1F, 1F, 0F).texOffs(54, 0).addBox(-0.5F, -1F, -1.5F, 1F, 1F, 0F).texOffs(56, 0).addBox(-0.5F, -1F, -0.5F, 1F, 1F, 0F), PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        part(parts, "tail", "body", CubeListBuilder.create().texOffs(34, 0).addBox(-0.5F, -1F, 0F, 1F, 2F, 3F).texOffs(42, 0).addBox(-1.5F, 0F, 3F, 3F, 0F, 2F), PartPose.offsetAndRotation(0F, -2F, 0F, 0.4F, 0F, 0F));
        return LayerDefinition.create(mesh, 64, 32).bakeRoot();
    }
}
