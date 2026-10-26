package com.example.skyblocks.client;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
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
    static final String[] NAMES = {"clownfish", "blue_tang", "yellow_tang", "neon_tetra", "angelfish", "goldfish", "betta", "guppy", "discus", "shark", "tuna", "manta_ray", "grouper", "jellyfish", "sardine", "ocean_sunfish", "basking_shark", "hermit_crab", "crab", "stingray", "octopus", "lobster", "shrimp", "anchor", "diving_helmet", "clam", "starfish", "scallop", "anemone"};
    static final float[] SCALES = {0.302222F, 0.288F, 0.3F, 0.228571F, 0.342857F, 0.269474F, 0.301176F, 0.246154F, 0.32F, 0.587755F, 0.470588F, 0.548571F, 0.55F, 0.8F, 0.274286F, 0.733333F, 0.705085F, 0.4096F, 0.397241F, 0.533333F, 0.580623F, 0.305882F, 0.147692F, 0.352821F, 0.3F, 0.44F, 0.373333F, 0.248889F, 0.363636F};
    /** How each gets about (swim, drift, glide near the bottom, crawl on it), and how far it reaches from its middle (blocks). */
    static final String[] MOVES = {"swim", "swim", "swim", "swim", "swim", "swim", "swim", "swim", "swim", "swim", "swim", "swim", "swim", "drift", "swim", "swim", "swim", "crawl", "crawl", "glide", "crawl", "crawl", "crawl", "decor", "decor", "decor", "decor", "decor", "sway"};
    static final float[] REACH = {0.17F, 0.18F, 0.15F, 0.1F, 0.15F, 0.16F, 0.16F, 0.1F, 0.15F, 0.9F, 0.5F, 0.6F, 0.55F, 0.25F, 0.12F, 0.55F, 1.3F, 0.16F, 0.18F, 0.5F, 0.275F, 0.325F, 0.12F, 0.215F, 0.15F, 0.11F, 0.105F, 0.07F, 0.125F};
    /** How long (along it) and how wide each model is, in its pixels (its body: no antennae or whip of a tail). */
    static final float[] LENGTH_PX = {18F, 20F, 16F, 14F, 14F, 19F, 17F, 13F, 15F, 49F, 34F, 21F, 32F, 10F, 14F, 24F, 59F, 12.5F, 14.5F, 14F, 15.1561F, 28F, 18F, 3F, 14F, 8F, 9F, 9F, 6F};
    static final float[] WIDTH_PX = {4F, 3F, 3F, 2F, 2F, 6F, 2F, 2F, 2F, 22F, 7F, 46F, 8F, 10F, 2F, 5F, 26F, 7F, 11F, 23F, 7F, 8F, 4F, 18F, 14F, 8F, 4F, 7F, 6F};

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
            case "anchor" -> anchor();
            case "diving_helmet" -> divingHelmet();
            case "clam" -> clam();
            case "starfish" -> starfish();
            case "scallop" -> scallop();
            case "anemone" -> anemone();
            default -> throw new IllegalArgumentException(name);
        };
    }

    private static void part(java.util.Map<String, PartDefinition> parts, String name, String parent, CubeListBuilder cubes, PartPose pose) {
        parts.put(name, parts.get(parent).addOrReplaceChild(name, cubes, pose));
    }

    /** clownfish: 0.34 blocks long, 18.0 px in the model. */
    static ModelPart clownfish() {
        MeshDefinition mesh = new MeshDefinition();
        java.util.Map<String, PartDefinition> parts = new java.util.HashMap<>();
        parts.put("", mesh.getRoot());
        part(parts, "body", "", CubeListBuilder.create().texOffs(26, 13).addBox(-1.5F, -1.5F, -7F, 3F, 3F, 2F).texOffs(0, 20).addBox(-0.5F, -2.5F, -7F, 1F, 1F, 2F).texOffs(6, 20).addBox(-0.5F, 1.5F, -7F, 1F, 1F, 2F).texOffs(28, 0).addBox(-2F, -2.5F, -5F, 4F, 5F, 2F).texOffs(12, 20).addBox(-1F, -3.5F, -5F, 2F, 1F, 2F).texOffs(20, 20).addBox(-1F, 2.5F, -5F, 2F, 1F, 2F).texOffs(40, 0).addBox(-2F, -2.5F, -3F, 4F, 5F, 2F).texOffs(28, 20).addBox(-1F, -3.5F, -3F, 2F, 1F, 2F).texOffs(36, 20).addBox(-1F, 2.5F, -3F, 2F, 1F, 2F).texOffs(0, 0).addBox(0F, -5.5F, -4F, 0F, 4F, 9F, new CubeDeformation(0.15F, 0F, 0F)).texOffs(0, 13).addBox(0F, 1.5F, 1F, 0F, 3F, 4F, new CubeDeformation(0.15F, 0F, 0F)).texOffs(58, 13).addBox(0F, 2.96533F, -2F, 0F, 2F, 2F, new CubeDeformation(0.15F, 0F, 0F)), PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        part(parts, "mid", "body", CubeListBuilder.create().texOffs(52, 0).addBox(-2F, -2.5F, 0F, 4F, 5F, 2F).texOffs(44, 20).addBox(-1F, -3.5F, 0F, 2F, 1F, 2F).texOffs(52, 20).addBox(-1F, 2.5F, 0F, 2F, 1F, 2F).texOffs(8, 13).addBox(-1.5F, -2F, 2F, 3F, 4F, 2F).texOffs(0, 23).addBox(-0.5F, -3F, 2F, 1F, 1F, 2F).texOffs(6, 23).addBox(-0.5F, 2F, 2F, 1F, 1F, 2F), PartPose.offsetAndRotation(0F, 0F, -1F, 0F, 0F, 0F));
        part(parts, "rear", "mid", CubeListBuilder.create().texOffs(36, 13).addBox(-1.5F, -1.5F, 0F, 3F, 3F, 2F).texOffs(12, 23).addBox(-0.5F, -2.5F, 0F, 1F, 1F, 2F).texOffs(18, 23).addBox(-0.5F, 1.5F, 0F, 1F, 1F, 2F).texOffs(18, 13).addBox(-1F, -2F, 2F, 2F, 4F, 2F), PartPose.offsetAndRotation(0F, 0F, 4F, 0F, 0F, 0F));
        part(parts, "tail", "rear", CubeListBuilder.create().texOffs(18, 0).addBox(0F, -3.5F, -1F, 0F, 7F, 5F, new CubeDeformation(0.15F, 0F, 0F)), PartPose.offsetAndRotation(0F, 0F, 4F, 0F, 0F, 0F));
        part(parts, "right_fin", "body", CubeListBuilder.create().texOffs(46, 13).addBox(0F, -1F, 0F, 0F, 2F, 3F, new CubeDeformation(0.15F, 0F, 0F)), PartPose.offsetAndRotation(-1.9819F, 0.84F, -3F, 0F, -0.6F, 0F));
        part(parts, "left_fin", "body", CubeListBuilder.create().texOffs(52, 13).addBox(0F, -1F, 0F, 0F, 2F, 3F, new CubeDeformation(0.15F, 0F, 0F)), PartPose.offsetAndRotation(1.9819F, 0.84F, -3F, 0F, 0.6F, 0F));
        return LayerDefinition.create(mesh, 64, 32).bakeRoot();
    }

    /** blue_tang: 0.36 blocks long, 20.0 px in the model. */
    static ModelPart blueTang() {
        MeshDefinition mesh = new MeshDefinition();
        java.util.Map<String, PartDefinition> parts = new java.util.HashMap<>();
        parts.put("", mesh.getRoot());
        part(parts, "body", "", CubeListBuilder.create().texOffs(18, 15).addBox(-1F, -3.5F, -8F, 2F, 7F, 2F).texOffs(26, 15).addBox(-1.5F, -3.5F, -6F, 3F, 7F, 2F).texOffs(16, 25).addBox(-0.5F, -4.5F, -6F, 1F, 1F, 2F).texOffs(22, 25).addBox(-0.5F, 3.5F, -6F, 1F, 1F, 2F).texOffs(50, 0).addBox(-1.5F, -4F, -4F, 3F, 8F, 2F).texOffs(28, 25).addBox(-0.5F, -5F, -4F, 1F, 1F, 2F).texOffs(34, 25).addBox(-0.5F, 4F, -4F, 1F, 1F, 2F).texOffs(0, 0).addBox(0F, -6.5F, -6F, 0F, 3F, 12F, new CubeDeformation(0.15F, 0F, 0F)).texOffs(34, 0).addBox(0F, 2.5F, -2F, 0F, 3F, 8F, new CubeDeformation(0.15F, 0F, 0F)).texOffs(12, 25).addBox(0F, 4.5F, -2F, 0F, 2F, 2F, new CubeDeformation(0.15F, 0F, 0F)), PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        part(parts, "mid", "body", CubeListBuilder.create().texOffs(0, 15).addBox(-1.5F, -4F, 0F, 3F, 8F, 2F).texOffs(40, 25).addBox(-0.5F, -5F, 0F, 1F, 1F, 2F).texOffs(46, 25).addBox(-0.5F, 4F, 0F, 1F, 1F, 2F).texOffs(36, 15).addBox(-1.5F, -3.5F, 2F, 3F, 7F, 2F).texOffs(52, 25).addBox(-0.5F, -4.5F, 2F, 1F, 1F, 2F).texOffs(58, 25).addBox(-0.5F, 3.5F, 2F, 1F, 1F, 2F), PartPose.offsetAndRotation(0F, 0F, -2F, 0F, 0F, 0F));
        part(parts, "rear", "mid", CubeListBuilder.create().texOffs(10, 15).addBox(-1F, -4F, 0F, 2F, 8F, 2F).texOffs(46, 15).addBox(-1F, -3F, 2F, 2F, 6F, 2F).texOffs(54, 15).addBox(-0.5F, -2F, 4F, 1F, 4F, 2F), PartPose.offsetAndRotation(0F, 0F, 4F, 0F, 0F, 0F));
        part(parts, "tail", "rear", CubeListBuilder.create().texOffs(24, 0).addBox(0F, -4F, -1F, 0F, 8F, 5F, new CubeDeformation(0.15F, 0F, 0F)), PartPose.offsetAndRotation(0F, 0F, 6F, 0F, 0F, 0F));
        part(parts, "right_fin", "body", CubeListBuilder.create().texOffs(0, 25).addBox(0F, -1.5F, 0F, 0F, 3F, 3F, new CubeDeformation(0.15F, 0F, 0F)), PartPose.offsetAndRotation(-1.45397F, 1.2F, -3F, 0F, -0.6F, 0F));
        part(parts, "left_fin", "body", CubeListBuilder.create().texOffs(6, 25).addBox(0F, -1.5F, 0F, 0F, 3F, 3F, new CubeDeformation(0.15F, 0F, 0F)), PartPose.offsetAndRotation(1.45397F, 1.2F, -3F, 0F, 0.6F, 0F));
        return LayerDefinition.create(mesh, 64, 32).bakeRoot();
    }

    /** yellow_tang: 0.3 blocks long, 16.0 px in the model. */
    static ModelPart yellowTang() {
        MeshDefinition mesh = new MeshDefinition();
        java.util.Map<String, PartDefinition> parts = new java.util.HashMap<>();
        parts.put("", mesh.getRoot());
        part(parts, "body", "", CubeListBuilder.create().texOffs(10, 13).addBox(-1F, -3.5F, -6.5F, 2F, 7F, 2F).texOffs(0, 13).addBox(-1.5F, -4F, -4.5F, 3F, 8F, 2F).texOffs(46, 13).addBox(-0.5F, -5F, -4.5F, 1F, 1F, 2F).texOffs(52, 13).addBox(-0.5F, 4F, -4.5F, 1F, 1F, 2F).texOffs(0, 0).addBox(0F, -9.5F, -3.5F, 0F, 5F, 8F, new CubeDeformation(0.15F, 0F, 0F)).texOffs(52, 0).addBox(0F, 3.5F, -1.5F, 0F, 5F, 6F, new CubeDeformation(0.15F, 0F, 0F)).texOffs(42, 13).addBox(0F, 5.44888F, -1.5F, 0F, 2F, 2F, new CubeDeformation(0.15F, 0F, 0F)), PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        part(parts, "mid", "body", CubeListBuilder.create().texOffs(16, 0).addBox(-1.5F, -5F, 0F, 3F, 10F, 2F).texOffs(58, 13).addBox(-0.5F, -6F, 0F, 1F, 1F, 2F).texOffs(0, 23).addBox(-0.5F, 5F, 0F, 1F, 1F, 2F).texOffs(26, 0).addBox(-1.5F, -5F, 2F, 3F, 10F, 2F).texOffs(6, 23).addBox(-0.5F, -6F, 2F, 1F, 1F, 2F).texOffs(12, 23).addBox(-0.5F, 5F, 2F, 1F, 1F, 2F), PartPose.offsetAndRotation(0F, 0F, -2.5F, 0F, 0F, 0F));
        part(parts, "rear", "mid", CubeListBuilder.create().texOffs(36, 0).addBox(-1F, -5F, 0F, 2F, 10F, 2F).texOffs(18, 13).addBox(-1F, -3.5F, 2F, 2F, 7F, 2F).texOffs(26, 13).addBox(-0.5F, -2.5F, 4F, 1F, 5F, 1F), PartPose.offsetAndRotation(0F, 0F, 4F, 0F, 0F, 0F));
        part(parts, "tail", "rear", CubeListBuilder.create().texOffs(44, 0).addBox(0F, -3.5F, -1F, 0F, 7F, 4F, new CubeDeformation(0.15F, 0F, 0F)), PartPose.offsetAndRotation(0F, 0F, 5F, 0F, 0F, 0F));
        part(parts, "right_fin", "body", CubeListBuilder.create().texOffs(30, 13).addBox(0F, -1F, 0F, 0F, 2F, 3F, new CubeDeformation(0.15F, 0F, 0F)), PartPose.offsetAndRotation(-1.40217F, 1.44F, -2.5F, 0F, -0.6F, 0F));
        part(parts, "left_fin", "body", CubeListBuilder.create().texOffs(36, 13).addBox(0F, -1F, 0F, 0F, 2F, 3F, new CubeDeformation(0.15F, 0F, 0F)), PartPose.offsetAndRotation(1.40217F, 1.44F, -2.5F, 0F, 0.6F, 0F));
        return LayerDefinition.create(mesh, 64, 32).bakeRoot();
    }

    /** neon_tetra: 0.2 blocks long, 14.0 px in the model. */
    static ModelPart neonTetra() {
        MeshDefinition mesh = new MeshDefinition();
        java.util.Map<String, PartDefinition> parts = new java.util.HashMap<>();
        parts.put("", mesh.getRoot());
        part(parts, "body", "", CubeListBuilder.create().texOffs(38, 0).addBox(-0.5F, -1.5F, -5F, 1F, 3F, 1F).texOffs(42, 0).addBox(-1F, -1.5F, -4F, 2F, 3F, 1F).texOffs(10, 0).addBox(-1F, -2F, -3F, 2F, 4F, 1F).texOffs(16, 0).addBox(-1F, -2F, -2F, 2F, 4F, 1F).texOffs(34, 0).addBox(0F, -3.5F, -1F, 0F, 3F, 2F, new CubeDeformation(0.15F, 0F, 0F)).texOffs(58, 0).addBox(0F, -0.5F, 1F, 0F, 2F, 2F, new CubeDeformation(0.15F, 0F, 0F)), PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        part(parts, "mid", "body", CubeListBuilder.create().texOffs(22, 0).addBox(-1F, -2F, 0F, 2F, 4F, 1F).texOffs(28, 0).addBox(-1F, -2F, 1F, 2F, 4F, 1F).texOffs(48, 0).addBox(-1F, -1.5F, 2F, 2F, 3F, 1F), PartPose.offsetAndRotation(0F, 0F, -1F, 0F, 0F, 0F));
        part(parts, "rear", "mid", CubeListBuilder.create().texOffs(54, 0).addBox(-0.5F, -1.5F, 0F, 1F, 3F, 1F).texOffs(0, 11).addBox(-0.5F, -1F, 1F, 1F, 2F, 1F).texOffs(4, 11).addBox(-0.5F, -1F, 2F, 1F, 2F, 1F), PartPose.offsetAndRotation(0F, 0F, 3F, 0F, 0F, 0F));
        part(parts, "tail", "rear", CubeListBuilder.create().texOffs(0, 0).addBox(0F, -3F, -1F, 0F, 6F, 5F, new CubeDeformation(0.15F, 0F, 0F)), PartPose.offsetAndRotation(0F, 0F, 3F, 0F, 0F, 0F));
        part(parts, "right_fin", "body", CubeListBuilder.create().texOffs(8, 11).addBox(0F, -0.5F, 0F, 0F, 1F, 2F, new CubeDeformation(0.15F, 0F, 0F)), PartPose.offsetAndRotation(-0.989946F, 0.48F, -2F, 0F, -0.6F, 0F));
        part(parts, "left_fin", "body", CubeListBuilder.create().texOffs(12, 11).addBox(0F, -0.5F, 0F, 0F, 1F, 2F, new CubeDeformation(0.15F, 0F, 0F)), PartPose.offsetAndRotation(0.989946F, 0.48F, -2F, 0F, 0.6F, 0F));
        return LayerDefinition.create(mesh, 64, 32).bakeRoot();
    }

    /** angelfish: 0.3 blocks long, 14.0 px in the model. */
    static ModelPart angelfish() {
        MeshDefinition mesh = new MeshDefinition();
        java.util.Map<String, PartDefinition> parts = new java.util.HashMap<>();
        parts.put("", mesh.getRoot());
        part(parts, "body", "", CubeListBuilder.create().texOffs(22, 18).addBox(-0.5F, -2.5F, -5F, 1F, 5F, 1F).texOffs(12, 18).addBox(-1F, -3.5F, -4F, 2F, 7F, 1F).texOffs(54, 0).addBox(-1F, -4F, -3F, 2F, 8F, 1F).texOffs(42, 0).addBox(-1F, -4.5F, -2F, 2F, 9F, 1F).texOffs(0, 0).addBox(0F, -12.5F, -3F, 0F, 10F, 8F, new CubeDeformation(0.15F, 0F, 0F)).texOffs(16, 0).addBox(0F, 2.5F, -3F, 0F, 10F, 8F, new CubeDeformation(0.15F, 0F, 0F)), PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        part(parts, "mid", "body", CubeListBuilder.create().texOffs(48, 0).addBox(-1F, -4.5F, 0F, 2F, 9F, 1F).texOffs(0, 18).addBox(-1F, -4F, 1F, 2F, 8F, 1F).texOffs(6, 18).addBox(-1F, -4F, 2F, 2F, 8F, 1F), PartPose.offsetAndRotation(0F, 0F, -1F, 0F, 0F, 0F));
        part(parts, "rear", "mid", CubeListBuilder.create().texOffs(18, 18).addBox(-0.5F, -3.5F, 0F, 1F, 7F, 1F).texOffs(26, 18).addBox(-0.5F, -2.5F, 1F, 1F, 5F, 1F).texOffs(30, 18).addBox(-0.5F, -2F, 2F, 1F, 4F, 1F), PartPose.offsetAndRotation(0F, 0F, 3F, 0F, 0F, 0F));
        part(parts, "tail", "rear", CubeListBuilder.create().texOffs(32, 0).addBox(0F, -3.5F, -1F, 0F, 7F, 5F, new CubeDeformation(0.15F, 0F, 0F)), PartPose.offsetAndRotation(0F, 0F, 3F, 0F, 0F, 0F));
        part(parts, "right_fin", "body", CubeListBuilder.create().texOffs(34, 18).addBox(0F, -1F, 0F, 0F, 2F, 3F, new CubeDeformation(0.15F, 0F, 0F)), PartPose.offsetAndRotation(-0.963175F, 1.08F, -2F, 0F, -0.6F, 0F));
        part(parts, "left_fin", "body", CubeListBuilder.create().texOffs(40, 18).addBox(0F, -1F, 0F, 0F, 2F, 3F, new CubeDeformation(0.15F, 0F, 0F)), PartPose.offsetAndRotation(0.963175F, 1.08F, -2F, 0F, 0.6F, 0F));
        return LayerDefinition.create(mesh, 64, 32).bakeRoot();
    }

    /** goldfish: 0.32 blocks long, 19.0 px in the model. */
    static ModelPart goldfish() {
        MeshDefinition mesh = new MeshDefinition();
        java.util.Map<String, PartDefinition> parts = new java.util.HashMap<>();
        parts.put("", mesh.getRoot());
        part(parts, "body", "", CubeListBuilder.create().texOffs(40, 0).addBox(-2.5F, -2F, -6F, 5F, 4F, 2F).texOffs(14, 24).addBox(-1.5F, -3F, -6F, 3F, 1F, 2F).texOffs(24, 24).addBox(-1.5F, 2F, -6F, 3F, 1F, 2F).texOffs(0, 18).addBox(-3F, -2F, -4F, 6F, 4F, 2F).texOffs(34, 24).addBox(-1F, -4F, -4F, 2F, 1F, 2F).texOffs(42, 24).addBox(-1F, 3F, -4F, 2F, 1F, 2F).texOffs(50, 24).addBox(-2F, -3F, -4F, 4F, 1F, 2F).texOffs(0, 28).addBox(-2F, 2F, -4F, 4F, 1F, 2F).texOffs(16, 0).addBox(0F, -6.5F, -2F, 0F, 4F, 5F, new CubeDeformation(0.15F, 0F, 0F)).texOffs(44, 18).addBox(0F, 1.5F, 2F, 0F, 3F, 2F, new CubeDeformation(0.15F, 0F, 0F)).texOffs(10, 24).addBox(0F, 3.5F, -1F, 0F, 2F, 2F, new CubeDeformation(0.15F, 0F, 0F)), PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        part(parts, "mid", "body", CubeListBuilder.create().texOffs(16, 18).addBox(-3F, -2F, 0F, 6F, 4F, 2F).texOffs(12, 28).addBox(-1F, -4F, 0F, 2F, 1F, 2F).texOffs(20, 28).addBox(-1F, 3F, 0F, 2F, 1F, 2F).texOffs(28, 28).addBox(-2F, -3F, 0F, 4F, 1F, 2F).texOffs(40, 28).addBox(-2F, 2F, 0F, 4F, 1F, 2F).texOffs(26, 0).addBox(-2.5F, -2.5F, 2F, 5F, 5F, 2F).texOffs(52, 28).addBox(-1.5F, -3.5F, 2F, 3F, 1F, 2F).texOffs(0, 31).addBox(-1.5F, 2.5F, 2F, 3F, 1F, 2F), PartPose.offsetAndRotation(0F, 0F, -2F, 0F, 0F, 0F));
        part(parts, "rear", "mid", CubeListBuilder.create().texOffs(32, 18).addBox(-2F, -2F, 0F, 4F, 4F, 2F).texOffs(10, 31).addBox(-1F, -3F, 0F, 2F, 1F, 2F).texOffs(18, 31).addBox(-1F, 2F, 0F, 2F, 1F, 2F).texOffs(0, 24).addBox(-1.5F, -1F, 2F, 3F, 2F, 2F).texOffs(26, 31).addBox(-0.5F, -2F, 2F, 1F, 1F, 2F).texOffs(32, 31).addBox(-0.5F, 1F, 2F, 1F, 1F, 2F), PartPose.offsetAndRotation(0F, 0F, 4F, 0F, 0F, 0F));
        part(parts, "tail", "rear", CubeListBuilder.create().texOffs(0, 0).addBox(0F, -5F, -1F, 0F, 10F, 8F, new CubeDeformation(0.15F, 0F, 0F)), PartPose.offsetAndRotation(0F, 0F, 4F, 0F, 0F, 0F));
        part(parts, "right_fin", "body", CubeListBuilder.create().texOffs(48, 18).addBox(0F, -1F, 0F, 0F, 2F, 3F, new CubeDeformation(0.15F, 0F, 0F)), PartPose.offsetAndRotation(-2.92635F, 0.96F, -2F, 0F, -0.6F, 0F));
        part(parts, "left_fin", "body", CubeListBuilder.create().texOffs(54, 18).addBox(0F, -1F, 0F, 0F, 2F, 3F, new CubeDeformation(0.15F, 0F, 0F)), PartPose.offsetAndRotation(2.92635F, 0.96F, -2F, 0F, 0.6F, 0F));
        return LayerDefinition.create(mesh, 64, 64).bakeRoot();
    }

    /** betta: 0.32 blocks long, 17.0 px in the model. */
    static ModelPart betta() {
        MeshDefinition mesh = new MeshDefinition();
        java.util.Map<String, PartDefinition> parts = new java.util.HashMap<>();
        parts.put("", mesh.getRoot());
        part(parts, "body", "", CubeListBuilder.create().texOffs(0, 21).addBox(-0.5F, -1.5F, -4.5F, 1F, 3F, 1F).texOffs(38, 0).addBox(-1F, -2F, -3.5F, 2F, 4F, 1F).texOffs(44, 0).addBox(-1F, -2F, -2.5F, 2F, 4F, 1F).texOffs(30, 0).addBox(0F, -6.5F, -0.5F, 0F, 6F, 4F, new CubeDeformation(0.15F, 0F, 0F)).texOffs(18, 0).addBox(0F, 0.5F, -1.5F, 0F, 7F, 6F, new CubeDeformation(0.15F, 0F, 0F)).texOffs(28, 21).addBox(0F, 1.48184F, -1.5F, 0F, 2F, 2F, new CubeDeformation(0.15F, 0F, 0F)), PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        part(parts, "mid", "body", CubeListBuilder.create().texOffs(50, 0).addBox(-1F, -2F, 0F, 2F, 4F, 1F).texOffs(56, 0).addBox(-1F, -2F, 1F, 2F, 4F, 1F).texOffs(4, 21).addBox(-1F, -1.5F, 2F, 2F, 3F, 1F), PartPose.offsetAndRotation(0F, 0F, -1.5F, 0F, 0F, 0F));
        part(parts, "rear", "mid", CubeListBuilder.create().texOffs(10, 21).addBox(-1F, -1.5F, 0F, 2F, 3F, 1F).texOffs(16, 21).addBox(-0.5F, -1.5F, 1F, 1F, 3F, 1F).texOffs(32, 21).addBox(-0.5F, -1F, 2F, 1F, 2F, 1F), PartPose.offsetAndRotation(0F, 0F, 3F, 0F, 0F, 0F));
        part(parts, "tail", "rear", CubeListBuilder.create().texOffs(0, 0).addBox(0F, -6F, -1F, 0F, 12F, 9F, new CubeDeformation(0.15F, 0F, 0F)), PartPose.offsetAndRotation(0F, 0F, 3F, 0F, 0F, 0F));
        part(parts, "right_fin", "body", CubeListBuilder.create().texOffs(20, 21).addBox(0F, -1F, 0F, 0F, 2F, 2F, new CubeDeformation(0.15F, 0F, 0F)), PartPose.offsetAndRotation(-0.990951F, 0.48F, -1.5F, 0F, -0.6F, 0F));
        part(parts, "left_fin", "body", CubeListBuilder.create().texOffs(24, 21).addBox(0F, -1F, 0F, 0F, 2F, 2F, new CubeDeformation(0.15F, 0F, 0F)), PartPose.offsetAndRotation(0.990951F, 0.48F, -1.5F, 0F, 0.6F, 0F));
        return LayerDefinition.create(mesh, 64, 32).bakeRoot();
    }

    /** guppy: 0.2 blocks long, 13.0 px in the model. */
    static ModelPart guppy() {
        MeshDefinition mesh = new MeshDefinition();
        java.util.Map<String, PartDefinition> parts = new java.util.HashMap<>();
        parts.put("", mesh.getRoot());
        part(parts, "body", "", CubeListBuilder.create().texOffs(36, 0).addBox(-0.5F, -1F, -3.5F, 1F, 2F, 1F).texOffs(18, 0).addBox(-1F, -1.5F, -2.5F, 2F, 3F, 1F).texOffs(24, 0).addBox(-1F, -1.5F, -1.5F, 2F, 3F, 1F).texOffs(14, 0).addBox(0F, -2.5F, 0.5F, 0F, 3F, 2F, new CubeDeformation(0.15F, 0F, 0F)), PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        part(parts, "mid", "body", CubeListBuilder.create().texOffs(30, 0).addBox(-1F, -1.5F, 0F, 2F, 3F, 1F).texOffs(40, 0).addBox(-1F, -1F, 1F, 2F, 2F, 1F), PartPose.offsetAndRotation(0F, 0F, -0.5F, 0F, 0F, 0F));
        part(parts, "rear", "mid", CubeListBuilder.create().texOffs(46, 0).addBox(-0.5F, -1F, 0F, 1F, 2F, 1F).texOffs(50, 0).addBox(-0.5F, -1F, 1F, 1F, 2F, 1F), PartPose.offsetAndRotation(0F, 0F, 2F, 0F, 0F, 0F));
        part(parts, "tail", "rear", CubeListBuilder.create().texOffs(0, 0).addBox(0F, -3.5F, -1F, 0F, 7F, 7F, new CubeDeformation(0.15F, 0F, 0F)), PartPose.offsetAndRotation(0F, 0F, 2F, 0F, 0F, 0F));
        part(parts, "right_fin", "body", CubeListBuilder.create().texOffs(54, 0).addBox(0F, -0.5F, 0F, 0F, 1F, 2F, new CubeDeformation(0.15F, 0F, 0F)), PartPose.offsetAndRotation(-0.990951F, 0.36F, -1.5F, 0F, -0.6F, 0F));
        part(parts, "left_fin", "body", CubeListBuilder.create().texOffs(58, 0).addBox(0F, -0.5F, 0F, 0F, 1F, 2F, new CubeDeformation(0.15F, 0F, 0F)), PartPose.offsetAndRotation(0.990951F, 0.36F, -1.5F, 0F, 0.6F, 0F));
        return LayerDefinition.create(mesh, 64, 32).bakeRoot();
    }

    /** discus: 0.3 blocks long, 15.0 px in the model. */
    static ModelPart discus() {
        MeshDefinition mesh = new MeshDefinition();
        java.util.Map<String, PartDefinition> parts = new java.util.HashMap<>();
        parts.put("", mesh.getRoot());
        part(parts, "body", "", CubeListBuilder.create().texOffs(30, 13).addBox(-0.5F, -3.5F, -6F, 1F, 7F, 1F).texOffs(22, 13).addBox(-0.5F, -4F, -5F, 1F, 8F, 1F).texOffs(30, 0).addBox(-1F, -5F, -4F, 2F, 10F, 1F).texOffs(18, 0).addBox(-1F, -5.5F, -3F, 2F, 11F, 1F).texOffs(0, 0).addBox(-1F, -6F, -2F, 2F, 12F, 1F).texOffs(36, 0).addBox(0F, -7.5F, -4F, 0F, 3F, 8F, new CubeDeformation(0.15F, 0F, 0F)).texOffs(8, 13).addBox(0F, 4.5F, -3F, 0F, 3F, 7F, new CubeDeformation(0.15F, 0F, 0F)).texOffs(54, 13).addBox(0F, 5.37014F, -1F, 0F, 2F, 2F, new CubeDeformation(0.15F, 0F, 0F)), PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        part(parts, "mid", "body", CubeListBuilder.create().texOffs(6, 0).addBox(-1F, -6F, 0F, 2F, 12F, 1F).texOffs(12, 0).addBox(-1F, -6F, 1F, 2F, 12F, 1F).texOffs(24, 0).addBox(-1F, -5.5F, 2F, 2F, 11F, 1F), PartPose.offsetAndRotation(0F, 0F, -1F, 0F, 0F, 0F));
        part(parts, "rear", "mid", CubeListBuilder.create().texOffs(52, 0).addBox(-1F, -4.5F, 0F, 2F, 9F, 1F).texOffs(26, 13).addBox(-0.5F, -4F, 1F, 1F, 8F, 1F).texOffs(34, 13).addBox(-0.5F, -3F, 2F, 1F, 6F, 1F).texOffs(38, 13).addBox(-0.5F, -2F, 3F, 1F, 4F, 1F), PartPose.offsetAndRotation(0F, 0F, 3F, 0F, 0F, 0F));
        part(parts, "tail", "rear", CubeListBuilder.create().texOffs(0, 13).addBox(0F, -3F, -1F, 0F, 6F, 4F, new CubeDeformation(0.15F, 0F, 0F)), PartPose.offsetAndRotation(0F, 0F, 4F, 0F, 0F, 0F));
        part(parts, "right_fin", "body", CubeListBuilder.create().texOffs(42, 13).addBox(0F, -1F, 0F, 0F, 2F, 3F, new CubeDeformation(0.15F, 0F, 0F)), PartPose.offsetAndRotation(-0.914225F, 1.44F, -2F, 0F, -0.6F, 0F));
        part(parts, "left_fin", "body", CubeListBuilder.create().texOffs(48, 13).addBox(0F, -1F, 0F, 0F, 2F, 3F, new CubeDeformation(0.15F, 0F, 0F)), PartPose.offsetAndRotation(0.914225F, 1.44F, -2F, 0F, 0.6F, 0F));
        return LayerDefinition.create(mesh, 64, 32).bakeRoot();
    }

    /** shark: 1.8 blocks long, 49.0 px in the model. */
    static ModelPart shark() {
        MeshDefinition mesh = new MeshDefinition();
        java.util.Map<String, PartDefinition> parts = new java.util.HashMap<>();
        parts.put("", mesh.getRoot());
        part(parts, "body", "", CubeListBuilder.create().texOffs(28, 63).addBox(-2F, -1.5F, -20F, 4F, 3F, 2F).texOffs(40, 63).addBox(-2.5F, -1.5F, -18F, 5F, 3F, 2F).texOffs(0, 74).addBox(-1.5F, -2.5F, -18F, 3F, 1F, 2F).texOffs(10, 74).addBox(-1.5F, 1.5F, -18F, 3F, 1F, 2F).texOffs(18, 50).addBox(-3.5F, -2F, -16F, 7F, 4F, 2F).texOffs(20, 74).addBox(-2.5F, -3F, -16F, 5F, 1F, 2F).texOffs(34, 74).addBox(-2.5F, 2F, -16F, 5F, 1F, 2F).texOffs(0, 36).addBox(-3.5F, -2.5F, -14F, 7F, 5F, 2F).texOffs(48, 74).addBox(-2.5F, -3.5F, -14F, 5F, 1F, 2F).texOffs(0, 77).addBox(-2.5F, 2.5F, -14F, 5F, 1F, 2F).texOffs(36, 50).addBox(-4F, -2F, -12F, 8F, 4F, 2F).texOffs(14, 77).addBox(-2F, -4F, -12F, 4F, 1F, 2F).texOffs(26, 77).addBox(-2F, 3F, -12F, 4F, 1F, 2F).texOffs(38, 77).addBox(-3F, -3F, -12F, 6F, 1F, 2F).texOffs(0, 80).addBox(-3F, 2F, -12F, 6F, 1F, 2F).texOffs(18, 36).addBox(-4.5F, -2.5F, -10F, 9F, 5F, 2F).texOffs(16, 80).addBox(-2.5F, -4.5F, -10F, 5F, 1F, 2F).texOffs(30, 80).addBox(-2.5F, 3.5F, -10F, 5F, 1F, 2F).texOffs(44, 80).addBox(-3.5F, -3.5F, -10F, 7F, 1F, 2F).texOffs(0, 83).addBox(-3.5F, 2.5F, -10F, 7F, 1F, 2F).texOffs(40, 36).addBox(-4.5F, -2.5F, -8F, 9F, 5F, 2F).texOffs(18, 83).addBox(-2.5F, -4.5F, -8F, 5F, 1F, 2F).texOffs(32, 83).addBox(-2.5F, 3.5F, -8F, 5F, 1F, 2F).texOffs(46, 83).addBox(-3.5F, -3.5F, -8F, 7F, 1F, 2F).texOffs(0, 86).addBox(-3.5F, 2.5F, -8F, 7F, 1F, 2F).texOffs(0, 43).addBox(-4.5F, -2.5F, -6F, 9F, 5F, 2F).texOffs(18, 86).addBox(-2.5F, -4.5F, -6F, 5F, 1F, 2F).texOffs(32, 86).addBox(-2.5F, 3.5F, -6F, 5F, 1F, 2F).texOffs(46, 86).addBox(-3.5F, -3.5F, -6F, 7F, 1F, 2F).texOffs(0, 89).addBox(-3.5F, 2.5F, -6F, 7F, 1F, 2F).texOffs(20, 0).addBox(0F, -11.5F, -6F, 0F, 9F, 7F, new CubeDeformation(0.15F, 0F, 0F)).texOffs(16, 63).addBox(0F, 0.5F, 10F, 0F, 3F, 3F, new CubeDeformation(0.15F, 0F, 0F)).texOffs(0, 28).addBox(-11F, 2F, -6F, 22F, 1F, 7F).texOffs(22, 63).addBox(0F, -6.5F, 10F, 0F, 3F, 3F, new CubeDeformation(0.15F, 0F, 0F)), PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        part(parts, "mid", "body", CubeListBuilder.create().texOffs(22, 43).addBox(-4.5F, -2.5F, 0F, 9F, 5F, 2F).texOffs(18, 89).addBox(-2.5F, -4.5F, 0F, 5F, 1F, 2F).texOffs(32, 89).addBox(-2.5F, 3.5F, 0F, 5F, 1F, 2F).texOffs(46, 89).addBox(-3.5F, -3.5F, 0F, 7F, 1F, 2F).texOffs(0, 92).addBox(-3.5F, 2.5F, 0F, 7F, 1F, 2F).texOffs(0, 57).addBox(-4F, -2F, 2F, 8F, 4F, 2F).texOffs(18, 92).addBox(-2F, -4F, 2F, 4F, 1F, 2F).texOffs(30, 92).addBox(-2F, 3F, 2F, 4F, 1F, 2F).texOffs(42, 92).addBox(-3F, -3F, 2F, 6F, 1F, 2F).texOffs(0, 95).addBox(-3F, 2F, 2F, 6F, 1F, 2F).texOffs(20, 57).addBox(-4F, -2F, 4F, 8F, 4F, 2F).texOffs(16, 95).addBox(-2F, -4F, 4F, 4F, 1F, 2F).texOffs(28, 95).addBox(-2F, 3F, 4F, 4F, 1F, 2F).texOffs(40, 95).addBox(-3F, -3F, 4F, 6F, 1F, 2F).texOffs(0, 98).addBox(-3F, 2F, 4F, 6F, 1F, 2F).texOffs(44, 43).addBox(-3.5F, -2.5F, 6F, 7F, 5F, 2F).texOffs(16, 98).addBox(-2.5F, -3.5F, 6F, 5F, 1F, 2F).texOffs(30, 98).addBox(-2.5F, 2.5F, 6F, 5F, 1F, 2F).texOffs(0, 50).addBox(-3.5F, -2.5F, 8F, 7F, 5F, 2F).texOffs(44, 98).addBox(-2.5F, -3.5F, 8F, 5F, 1F, 2F).texOffs(0, 101).addBox(-2.5F, 2.5F, 8F, 5F, 1F, 2F).texOffs(40, 57).addBox(-3F, -2F, 10F, 6F, 4F, 2F).texOffs(14, 101).addBox(-2F, -3F, 10F, 4F, 1F, 2F).texOffs(26, 101).addBox(-2F, 2F, 10F, 4F, 1F, 2F), PartPose.offsetAndRotation(0F, 0F, -4F, 0F, 0F, 0F));
        part(parts, "rear", "mid", CubeListBuilder.create().texOffs(0, 63).addBox(-3F, -2F, 0F, 6F, 4F, 2F).texOffs(38, 101).addBox(-2F, -3F, 0F, 4F, 1F, 2F).texOffs(50, 101).addBox(-2F, 2F, 0F, 4F, 1F, 2F).texOffs(0, 69).addBox(-2.5F, -1.5F, 2F, 5F, 3F, 2F).texOffs(0, 104).addBox(-1.5F, -2.5F, 2F, 3F, 1F, 2F).texOffs(10, 104).addBox(-1.5F, 1.5F, 2F, 3F, 1F, 2F).texOffs(24, 69).addBox(-2F, -1F, 4F, 4F, 2F, 2F).texOffs(20, 104).addBox(-1F, -2F, 4F, 2F, 1F, 2F).texOffs(28, 104).addBox(-1F, 1F, 4F, 2F, 1F, 2F).texOffs(36, 69).addBox(-2F, -1F, 6F, 4F, 2F, 2F).texOffs(36, 104).addBox(-1F, -2F, 6F, 2F, 1F, 2F).texOffs(44, 104).addBox(-1F, 1F, 6F, 2F, 1F, 2F).texOffs(14, 69).addBox(-1.5F, -1.5F, 8F, 3F, 3F, 2F).texOffs(48, 69).addBox(-1F, -1F, 10F, 2F, 2F, 2F), PartPose.offsetAndRotation(0F, 0F, 12F, 0F, 0F, 0F));
        part(parts, "tail", "rear", CubeListBuilder.create().texOffs(0, 0).addBox(0F, -9F, -1F, 0F, 18F, 10F, new CubeDeformation(0.15F, 0F, 0F)), PartPose.offsetAndRotation(0F, 0F, 12F, 0F, 0F, 0F));
        return LayerDefinition.create(mesh, 64, 128).bakeRoot();
    }

    /** tuna: 1 blocks long, 34.0 px in the model. */
    static ModelPart tuna() {
        MeshDefinition mesh = new MeshDefinition();
        java.util.Map<String, PartDefinition> parts = new java.util.HashMap<>();
        parts.put("", mesh.getRoot());
        part(parts, "body", "", CubeListBuilder.create().texOffs(50, 37).addBox(-1.5F, -1F, -14F, 3F, 2F, 2F).texOffs(16, 43).addBox(-0.5F, -2F, -14F, 1F, 1F, 2F).texOffs(22, 43).addBox(-0.5F, 1F, -14F, 1F, 1F, 2F).texOffs(34, 30).addBox(-2.5F, -2F, -12F, 5F, 4F, 2F).texOffs(28, 43).addBox(-1.5F, -3F, -12F, 3F, 1F, 2F).texOffs(38, 43).addBox(-1.5F, 2F, -12F, 3F, 1F, 2F).texOffs(0, 23).addBox(-3F, -2.5F, -10F, 6F, 5F, 2F).texOffs(48, 43).addBox(-2F, -3.5F, -10F, 4F, 1F, 2F).texOffs(0, 47).addBox(-2F, 2.5F, -10F, 4F, 1F, 2F).texOffs(48, 30).addBox(-3F, -2F, -8F, 6F, 4F, 2F).texOffs(12, 47).addBox(-1F, -4F, -8F, 2F, 1F, 2F).texOffs(20, 47).addBox(-1F, 3F, -8F, 2F, 1F, 2F).texOffs(28, 47).addBox(-2F, -3F, -8F, 4F, 1F, 2F).texOffs(40, 47).addBox(-2F, 2F, -8F, 4F, 1F, 2F).texOffs(16, 23).addBox(-3.5F, -2.5F, -6F, 7F, 5F, 2F).texOffs(52, 47).addBox(-1.5F, -4.5F, -6F, 3F, 1F, 2F).texOffs(0, 50).addBox(-1.5F, 3.5F, -6F, 3F, 1F, 2F).texOffs(10, 50).addBox(-2.5F, -3.5F, -6F, 5F, 1F, 2F).texOffs(24, 50).addBox(-2.5F, 2.5F, -6F, 5F, 1F, 2F).texOffs(14, 0).addBox(0F, -8.5F, -6F, 0F, 6F, 4F, new CubeDeformation(0.15F, 0F, 0F)).texOffs(22, 0).addBox(0F, 2.5F, 1F, 0F, 5F, 3F, new CubeDeformation(0.15F, 0F, 0F)).texOffs(12, 62).addBox(0F, -3F, 4F, 0F, 1F, 1F, new CubeDeformation(0.15F, 0F, 0F)).texOffs(14, 62).addBox(0F, 2F, 4F, 0F, 1F, 1F, new CubeDeformation(0.15F, 0F, 0F)).texOffs(16, 62).addBox(0F, -3F, 6F, 0F, 1F, 1F, new CubeDeformation(0.15F, 0F, 0F)).texOffs(18, 62).addBox(0F, 2F, 6F, 0F, 1F, 1F, new CubeDeformation(0.15F, 0F, 0F)).texOffs(20, 62).addBox(0F, -2.5F, 8F, 0F, 1F, 1F, new CubeDeformation(0.15F, 0F, 0F)).texOffs(22, 62).addBox(0F, 1.5F, 8F, 0F, 1F, 1F, new CubeDeformation(0.15F, 0F, 0F)).texOffs(24, 62).addBox(0F, -2.5F, 10F, 0F, 1F, 1F, new CubeDeformation(0.15F, 0F, 0F)).texOffs(26, 62).addBox(0F, 1.5F, 10F, 0F, 1F, 1F, new CubeDeformation(0.15F, 0F, 0F)), PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        part(parts, "mid", "body", CubeListBuilder.create().texOffs(34, 23).addBox(-3.5F, -2.5F, 0F, 7F, 5F, 2F).texOffs(38, 50).addBox(-1.5F, -4.5F, 0F, 3F, 1F, 2F).texOffs(48, 50).addBox(-1.5F, 3.5F, 0F, 3F, 1F, 2F).texOffs(0, 53).addBox(-2.5F, -3.5F, 0F, 5F, 1F, 2F).texOffs(14, 53).addBox(-2.5F, 2.5F, 0F, 5F, 1F, 2F).texOffs(0, 30).addBox(-3.5F, -2.5F, 2F, 7F, 5F, 2F).texOffs(28, 53).addBox(-1.5F, -4.5F, 2F, 3F, 1F, 2F).texOffs(38, 53).addBox(-1.5F, 3.5F, 2F, 3F, 1F, 2F).texOffs(48, 53).addBox(-2.5F, -3.5F, 2F, 5F, 1F, 2F).texOffs(0, 56).addBox(-2.5F, 2.5F, 2F, 5F, 1F, 2F).texOffs(0, 37).addBox(-3F, -2F, 4F, 6F, 4F, 2F).texOffs(14, 56).addBox(-1F, -4F, 4F, 2F, 1F, 2F).texOffs(22, 56).addBox(-1F, 3F, 4F, 2F, 1F, 2F).texOffs(30, 56).addBox(-2F, -3F, 4F, 4F, 1F, 2F).texOffs(42, 56).addBox(-2F, 2F, 4F, 4F, 1F, 2F).texOffs(18, 30).addBox(-3F, -2.5F, 6F, 6F, 5F, 2F).texOffs(0, 59).addBox(-2F, -3.5F, 6F, 4F, 1F, 2F).texOffs(12, 59).addBox(-2F, 2.5F, 6F, 4F, 1F, 2F).texOffs(16, 37).addBox(-2.5F, -2F, 8F, 5F, 4F, 2F).texOffs(24, 59).addBox(-1.5F, -3F, 8F, 3F, 1F, 2F).texOffs(34, 59).addBox(-1.5F, 2F, 8F, 3F, 1F, 2F), PartPose.offsetAndRotation(0F, 0F, -4F, 0F, 0F, 0F));
        part(parts, "rear", "mid", CubeListBuilder.create().texOffs(30, 37).addBox(-2F, -1.5F, 0F, 4F, 3F, 2F).texOffs(44, 59).addBox(-1F, -2.5F, 0F, 2F, 1F, 2F).texOffs(52, 59).addBox(-1F, 1.5F, 0F, 2F, 1F, 2F).texOffs(0, 43).addBox(-1.5F, -1F, 2F, 3F, 2F, 2F).texOffs(0, 62).addBox(-0.5F, -2F, 2F, 1F, 1F, 2F).texOffs(6, 62).addBox(-0.5F, 1F, 2F, 1F, 1F, 2F).texOffs(42, 37).addBox(-1F, -1.5F, 4F, 2F, 3F, 2F).texOffs(10, 43).addBox(-0.5F, -1F, 6F, 1F, 2F, 2F), PartPose.offsetAndRotation(0F, 0F, 10F, 0F, 0F, 0F));
        part(parts, "tail", "rear", CubeListBuilder.create().texOffs(0, 0).addBox(0F, -8F, -1F, 0F, 16F, 7F, new CubeDeformation(0.15F, 0F, 0F)), PartPose.offsetAndRotation(0F, 0F, 8F, 0F, 0F, 0F));
        part(parts, "right_fin", "body", CubeListBuilder.create().texOffs(28, 0).addBox(0F, -1F, 0F, 0F, 2F, 6F, new CubeDeformation(0.15F, 0F, 0F)), PartPose.offsetAndRotation(-3.36037F, 1.08F, -6F, 0F, -0.6F, 0F));
        part(parts, "left_fin", "body", CubeListBuilder.create().texOffs(40, 0).addBox(0F, -1F, 0F, 0F, 2F, 6F, new CubeDeformation(0.15F, 0F, 0F)), PartPose.offsetAndRotation(3.36037F, 1.08F, -6F, 0F, 0.6F, 0F));
        return LayerDefinition.create(mesh, 64, 128).bakeRoot();
    }

    /** manta_ray: 1.2 blocks long, 35.0 px in the model. */
    static ModelPart mantaRay() {
        MeshDefinition mesh = new MeshDefinition();
        java.util.Map<String, PartDefinition> parts = new java.util.HashMap<>();
        parts.put("", mesh.getRoot());
        part(parts, "body", "", CubeListBuilder.create().texOffs(24, 48).addBox(-4F, -1.5F, -9F, 8F, 3F, 2F).texOffs(0, 59).addBox(-4.5F, -1F, -7F, 9F, 2F, 2F).texOffs(12, 67).addBox(-3.5F, -2F, -7F, 7F, 1F, 2F).texOffs(30, 67).addBox(-3.5F, 1F, -7F, 7F, 1F, 2F).texOffs(22, 59).addBox(-5F, -1F, -5F, 10F, 2F, 2F).texOffs(0, 71).addBox(-4F, -2F, -5F, 8F, 1F, 2F).texOffs(20, 71).addBox(-4F, 1F, -5F, 8F, 1F, 2F).texOffs(0, 63).addBox(-5F, -1F, -3F, 10F, 2F, 2F).texOffs(40, 71).addBox(-4F, -2F, -3F, 8F, 1F, 2F).texOffs(0, 74).addBox(-4F, 1F, -3F, 8F, 1F, 2F).texOffs(24, 63).addBox(-4.5F, -1F, -1F, 9F, 2F, 2F).texOffs(20, 74).addBox(-3.5F, -2F, -1F, 7F, 1F, 2F).texOffs(38, 74).addBox(-3.5F, 1F, -1F, 7F, 1F, 2F).texOffs(44, 48).addBox(-4F, -1.5F, 1F, 8F, 3F, 2F).texOffs(0, 54).addBox(-3.5F, -1.5F, 3F, 7F, 3F, 2F).texOffs(46, 63).addBox(-2.5F, -1F, 5F, 5F, 2F, 2F).texOffs(0, 67).addBox(-2F, -1F, 7F, 4F, 2F, 2F).texOffs(0, 48).addBox(-5F, -1F, -12F, 2F, 2F, 4F).texOffs(12, 48).addBox(3F, -1F, -12F, 2F, 2F, 4F), PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        part(parts, "right_wing", "body", CubeListBuilder.create().texOffs(30, 0).addBox(-3F, -0.5F, -4F, 3F, 1F, 12F).texOffs(30, 15).addBox(-6F, -0.5F, -2.7F, 3F, 1F, 10F).texOffs(26, 28).addBox(-9F, -0.5F, -1.4F, 3F, 1F, 8F), PartPose.offsetAndRotation(-5F, 0F, -3F, 0F, 0F, 0F));
        part(parts, "right_wing_tip", "right_wing", CubeListBuilder.create().texOffs(22, 39).addBox(-3F, -0.5F, -0.1F, 3F, 1F, 6F).texOffs(18, 54).addBox(-6F, -0.5F, 1.2F, 3F, 1F, 4F).texOffs(0, 77).addBox(-9F, -0.5F, 2.5F, 3F, 1F, 2F), PartPose.offsetAndRotation(-9F, 0F, 0F, 0F, 0F, 0F));
        part(parts, "left_wing", "body", CubeListBuilder.create().texOffs(0, 15).addBox(0F, -0.5F, -4F, 3F, 1F, 12F).texOffs(0, 28).addBox(3F, -0.5F, -2.7F, 3F, 1F, 10F).texOffs(0, 39).addBox(6F, -0.5F, -1.4F, 3F, 1F, 8F), PartPose.offsetAndRotation(5F, 0F, -3F, 0F, 0F, 0F));
        part(parts, "left_wing_tip", "left_wing", CubeListBuilder.create().texOffs(40, 39).addBox(0F, -0.5F, -0.1F, 3F, 1F, 6F).texOffs(32, 54).addBox(3F, -0.5F, 1.2F, 3F, 1F, 4F).texOffs(10, 77).addBox(6F, -0.5F, 2.5F, 3F, 1F, 2F), PartPose.offsetAndRotation(9F, 0F, 0F, 0F, 0F, 0F));
        part(parts, "tail", "body", CubeListBuilder.create().texOffs(0, 0).addBox(-0.5F, -0.5F, 0F, 1F, 1F, 14F), PartPose.offsetAndRotation(0F, 0F, 9F, 0F, 0F, 0F));
        return LayerDefinition.create(mesh, 64, 128).bakeRoot();
    }

    /** grouper: 1.1 blocks long, 32.0 px in the model. */
    static ModelPart grouper() {
        MeshDefinition mesh = new MeshDefinition();
        java.util.Map<String, PartDefinition> parts = new java.util.HashMap<>();
        parts.put("", mesh.getRoot());
        part(parts, "body", "", CubeListBuilder.create().texOffs(18, 36).addBox(-3F, -2.5F, -13F, 6F, 5F, 2F).texOffs(42, 51).addBox(-2F, -3.5F, -13F, 4F, 1F, 2F).texOffs(0, 57).addBox(-2F, 2.5F, -13F, 4F, 1F, 2F).texOffs(34, 36).addBox(-3.5F, -2.5F, -11F, 7F, 5F, 2F).texOffs(12, 57).addBox(-1.5F, -4.5F, -11F, 3F, 1F, 2F).texOffs(22, 57).addBox(-1.5F, 3.5F, -11F, 3F, 1F, 2F).texOffs(32, 57).addBox(-2.5F, -3.5F, -11F, 5F, 1F, 2F).texOffs(46, 57).addBox(-2.5F, 2.5F, -11F, 5F, 1F, 2F).texOffs(10, 27).addBox(-3.5F, -3F, -9F, 7F, 6F, 2F).texOffs(0, 60).addBox(-1.5F, -5F, -9F, 3F, 1F, 2F).texOffs(10, 60).addBox(-1.5F, 4F, -9F, 3F, 1F, 2F).texOffs(20, 60).addBox(-2.5F, -4F, -9F, 5F, 1F, 2F).texOffs(34, 60).addBox(-2.5F, 3F, -9F, 5F, 1F, 2F).texOffs(40, 0).addBox(-4F, -3.5F, -7F, 8F, 7F, 2F).texOffs(48, 60).addBox(-2F, -5.5F, -7F, 4F, 1F, 2F).texOffs(0, 63).addBox(-2F, 4.5F, -7F, 4F, 1F, 2F).texOffs(12, 63).addBox(-3F, -4.5F, -7F, 6F, 1F, 2F).texOffs(28, 63).addBox(-3F, 3.5F, -7F, 6F, 1F, 2F).texOffs(0, 18).addBox(-4F, -3.5F, -5F, 8F, 7F, 2F).texOffs(44, 63).addBox(-2F, -5.5F, -5F, 4F, 1F, 2F).texOffs(0, 66).addBox(-2F, 4.5F, -5F, 4F, 1F, 2F).texOffs(12, 66).addBox(-3F, -4.5F, -5F, 6F, 1F, 2F).texOffs(28, 66).addBox(-3F, 3.5F, -5F, 6F, 1F, 2F).texOffs(0, 0).addBox(0F, -7.5F, -7F, 0F, 4F, 14F, new CubeDeformation(0.15F, 0F, 0F)).texOffs(40, 18).addBox(0F, 2.5F, 3F, 0F, 4F, 5F, new CubeDeformation(0.15F, 0F, 0F)).texOffs(38, 51).addBox(0F, 4.94098F, -3F, 0F, 2F, 2F, new CubeDeformation(0.15F, 0F, 0F)).texOffs(30, 44).addBox(-3F, 1F, -14F, 6F, 3F, 4F), PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        part(parts, "mid", "body", CubeListBuilder.create().texOffs(20, 18).addBox(-4F, -3.5F, 0F, 8F, 7F, 2F).texOffs(44, 66).addBox(-2F, -5.5F, 0F, 4F, 1F, 2F).texOffs(0, 69).addBox(-2F, 4.5F, 0F, 4F, 1F, 2F).texOffs(12, 69).addBox(-3F, -4.5F, 0F, 6F, 1F, 2F).texOffs(28, 69).addBox(-3F, 3.5F, 0F, 6F, 1F, 2F).texOffs(28, 27).addBox(-4F, -3F, 2F, 8F, 6F, 2F).texOffs(44, 69).addBox(-2F, -5F, 2F, 4F, 1F, 2F).texOffs(0, 72).addBox(-2F, 4F, 2F, 4F, 1F, 2F).texOffs(12, 72).addBox(-3F, -4F, 2F, 6F, 1F, 2F).texOffs(28, 72).addBox(-3F, 3F, 2F, 6F, 1F, 2F).texOffs(0, 36).addBox(-3.5F, -3F, 4F, 7F, 6F, 2F).texOffs(44, 72).addBox(-1.5F, -5F, 4F, 3F, 1F, 2F).texOffs(54, 72).addBox(-1.5F, 4F, 4F, 3F, 1F, 2F).texOffs(0, 75).addBox(-2.5F, -4F, 4F, 5F, 1F, 2F).texOffs(14, 75).addBox(-2.5F, 3F, 4F, 5F, 1F, 2F).texOffs(0, 44).addBox(-3F, -2.5F, 6F, 6F, 5F, 2F).texOffs(28, 75).addBox(-1F, -4.5F, 6F, 2F, 1F, 2F).texOffs(36, 75).addBox(-1F, 3.5F, 6F, 2F, 1F, 2F).texOffs(44, 75).addBox(-2F, -3.5F, 6F, 4F, 1F, 2F).texOffs(0, 78).addBox(-2F, 2.5F, 6F, 4F, 1F, 2F), PartPose.offsetAndRotation(0F, 0F, -3F, 0F, 0F, 0F));
        part(parts, "rear", "mid", CubeListBuilder.create().texOffs(0, 51).addBox(-3F, -2F, 0F, 6F, 4F, 2F).texOffs(12, 78).addBox(-1F, -4F, 0F, 2F, 1F, 2F).texOffs(20, 78).addBox(-1F, 3F, 0F, 2F, 1F, 2F).texOffs(28, 78).addBox(-2F, -3F, 0F, 4F, 1F, 2F).texOffs(40, 78).addBox(-2F, 2F, 0F, 4F, 1F, 2F).texOffs(16, 44).addBox(-2.5F, -2.5F, 2F, 5F, 5F, 2F).texOffs(52, 78).addBox(-1.5F, -3.5F, 2F, 3F, 1F, 2F).texOffs(0, 81).addBox(-1.5F, 2.5F, 2F, 3F, 1F, 2F).texOffs(16, 51).addBox(-2F, -2F, 4F, 4F, 4F, 2F).texOffs(10, 81).addBox(-1F, -3F, 4F, 2F, 1F, 2F).texOffs(18, 81).addBox(-1F, 2F, 4F, 2F, 1F, 2F).texOffs(28, 51).addBox(-1.5F, -1.5F, 6F, 3F, 3F, 2F).texOffs(26, 81).addBox(-0.5F, -2.5F, 6F, 1F, 1F, 2F).texOffs(32, 81).addBox(-0.5F, 1.5F, 6F, 1F, 1F, 2F), PartPose.offsetAndRotation(0F, 0F, 8F, 0F, 0F, 0F));
        part(parts, "tail", "rear", CubeListBuilder.create().texOffs(28, 0).addBox(0F, -5F, -1F, 0F, 10F, 6F, new CubeDeformation(0.15F, 0F, 0F)), PartPose.offsetAndRotation(0F, 0F, 8F, 0F, 0F, 0F));
        part(parts, "right_fin", "body", CubeListBuilder.create().texOffs(50, 18).addBox(0F, -2F, 0F, 0F, 4F, 5F, new CubeDeformation(0.15F, 0F, 0F)), PartPose.offsetAndRotation(-3.96783F, 1.32F, -5F, 0F, -0.6F, 0F));
        part(parts, "left_fin", "body", CubeListBuilder.create().texOffs(0, 27).addBox(0F, -2F, 0F, 0F, 4F, 5F, new CubeDeformation(0.15F, 0F, 0F)), PartPose.offsetAndRotation(3.96783F, 1.32F, -5F, 0F, 0.6F, 0F));
        return LayerDefinition.create(mesh, 64, 128).bakeRoot();
    }

    /** jellyfish: 0.5 blocks long, 10.0 px in the model. */
    static ModelPart jellyfish() {
        MeshDefinition mesh = new MeshDefinition();
        java.util.Map<String, PartDefinition> parts = new java.util.HashMap<>();
        parts.put("", mesh.getRoot());
        part(parts, "body", "", CubeListBuilder.create().texOffs(16, 0).addBox(-5F, -4F, -5F, 10F, 2F, 10F).texOffs(0, 15).addBox(-4.5F, -6F, -4.5F, 9F, 2F, 9F).texOffs(24, 37).addBox(-3.5F, -8F, -3.5F, 7F, 2F, 7F).texOffs(0, 47).addBox(-2F, -9F, -2F, 4F, 1F, 4F).texOffs(0, 26).addBox(-5F, -2F, -5F, 10F, 1F, 10F).texOffs(40, 26).addBox(-3F, -6F, -3F, 6F, 5F, 6F), PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        part(parts, "arm0", "body", CubeListBuilder.create().texOffs(0, 37).addBox(-1F, 0F, -0.5F, 2F, 9F, 1F), PartPose.offsetAndRotation(1.10527F, -1F, 0.467302F, 0F, 0F, 0F));
        part(parts, "arm1", "body", CubeListBuilder.create().texOffs(6, 37).addBox(-1F, 0F, -0.5F, 2F, 9F, 1F), PartPose.offsetAndRotation(-0.467302F, -1F, 1.10527F, 0F, 0F, 0F));
        part(parts, "arm2", "body", CubeListBuilder.create().texOffs(12, 37).addBox(-1F, 0F, -0.5F, 2F, 9F, 1F), PartPose.offsetAndRotation(-1.10527F, -1F, -0.467302F, 0F, 0F, 0F));
        part(parts, "arm3", "body", CubeListBuilder.create().texOffs(18, 37).addBox(-1F, 0F, -0.5F, 2F, 9F, 1F), PartPose.offsetAndRotation(0.467302F, -1F, -1.10527F, 0F, 0F, 0F));
        part(parts, "tentacle0", "body", CubeListBuilder.create().texOffs(0, 0).addBox(0F, 0F, -0.5F, 0F, 14F, 1F, new CubeDeformation(0.15F, 0F, 0F)), PartPose.offsetAndRotation(4.2F, -1F, 0F, 0F, 0F, 0F));
        part(parts, "tentacle1", "body", CubeListBuilder.create().texOffs(2, 0).addBox(0F, 0F, -0.5F, 0F, 14F, 1F, new CubeDeformation(0.15F, 0F, 0F)), PartPose.offsetAndRotation(2.96985F, -1F, 2.96985F, 0F, 0F, 0F));
        part(parts, "tentacle2", "body", CubeListBuilder.create().texOffs(4, 0).addBox(0F, 0F, -0.5F, 0F, 14F, 1F, new CubeDeformation(0.15F, 0F, 0F)), PartPose.offsetAndRotation(2.57176e-16F, -1F, 4.2F, 0F, 0F, 0F));
        part(parts, "tentacle3", "body", CubeListBuilder.create().texOffs(6, 0).addBox(0F, 0F, -0.5F, 0F, 14F, 1F, new CubeDeformation(0.15F, 0F, 0F)), PartPose.offsetAndRotation(-2.96985F, -1F, 2.96985F, 0F, 0F, 0F));
        part(parts, "tentacle4", "body", CubeListBuilder.create().texOffs(8, 0).addBox(0F, 0F, -0.5F, 0F, 14F, 1F, new CubeDeformation(0.15F, 0F, 0F)), PartPose.offsetAndRotation(-4.2F, -1F, 5.14352e-16F, 0F, 0F, 0F));
        part(parts, "tentacle5", "body", CubeListBuilder.create().texOffs(10, 0).addBox(0F, 0F, -0.5F, 0F, 14F, 1F, new CubeDeformation(0.15F, 0F, 0F)), PartPose.offsetAndRotation(-2.96985F, -1F, -2.96985F, 0F, 0F, 0F));
        part(parts, "tentacle6", "body", CubeListBuilder.create().texOffs(12, 0).addBox(0F, 0F, -0.5F, 0F, 14F, 1F, new CubeDeformation(0.15F, 0F, 0F)), PartPose.offsetAndRotation(-7.71527e-16F, -1F, -4.2F, 0F, 0F, 0F));
        part(parts, "tentacle7", "body", CubeListBuilder.create().texOffs(14, 0).addBox(0F, 0F, -0.5F, 0F, 14F, 1F, new CubeDeformation(0.15F, 0F, 0F)), PartPose.offsetAndRotation(2.96985F, -1F, -2.96985F, 0F, 0F, 0F));
        return LayerDefinition.create(mesh, 64, 64).bakeRoot();
    }

    /** sardine: 0.24 blocks long, 14.0 px in the model. */
    static ModelPart sardine() {
        MeshDefinition mesh = new MeshDefinition();
        java.util.Map<String, PartDefinition> parts = new java.util.HashMap<>();
        parts.put("", mesh.getRoot());
        part(parts, "body", "", CubeListBuilder.create().texOffs(54, 0).addBox(-0.5F, -1F, -5F, 1F, 2F, 1F).texOffs(38, 0).addBox(-1F, -1.5F, -4F, 2F, 3F, 1F).texOffs(10, 0).addBox(-1F, -2F, -3F, 2F, 4F, 1F).texOffs(16, 0).addBox(-1F, -2F, -2F, 2F, 4F, 1F).texOffs(34, 0).addBox(0F, -3.5F, -1F, 0F, 3F, 2F, new CubeDeformation(0.15F, 0F, 0F)), PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        part(parts, "mid", "body", CubeListBuilder.create().texOffs(22, 0).addBox(-1F, -2F, 0F, 2F, 4F, 1F).texOffs(28, 0).addBox(-1F, -2F, 1F, 2F, 4F, 1F).texOffs(44, 0).addBox(-1F, -1.5F, 2F, 2F, 3F, 1F), PartPose.offsetAndRotation(0F, 0F, -1F, 0F, 0F, 0F));
        part(parts, "rear", "mid", CubeListBuilder.create().texOffs(50, 0).addBox(-0.5F, -1.5F, 0F, 1F, 3F, 1F).texOffs(58, 0).addBox(-0.5F, -1F, 1F, 1F, 2F, 1F).texOffs(0, 11).addBox(-0.5F, -1F, 2F, 1F, 2F, 1F), PartPose.offsetAndRotation(0F, 0F, 3F, 0F, 0F, 0F));
        part(parts, "tail", "rear", CubeListBuilder.create().texOffs(0, 0).addBox(0F, -3F, -1F, 0F, 6F, 5F, new CubeDeformation(0.15F, 0F, 0F)), PartPose.offsetAndRotation(0F, 0F, 3F, 0F, 0F, 0F));
        part(parts, "right_fin", "body", CubeListBuilder.create().texOffs(4, 11).addBox(0F, -0.5F, 0F, 0F, 1F, 2F, new CubeDeformation(0.15F, 0F, 0F)), PartPose.offsetAndRotation(-0.966244F, 0.48F, -2F, 0F, -0.6F, 0F));
        part(parts, "left_fin", "body", CubeListBuilder.create().texOffs(8, 11).addBox(0F, -0.5F, 0F, 0F, 1F, 2F, new CubeDeformation(0.15F, 0F, 0F)), PartPose.offsetAndRotation(0.966244F, 0.48F, -2F, 0F, 0.6F, 0F));
        return LayerDefinition.create(mesh, 64, 32).bakeRoot();
    }

    /** ocean_sunfish: 1.1 blocks long, 24.0 px in the model. */
    static ModelPart oceanSunfish() {
        MeshDefinition mesh = new MeshDefinition();
        java.util.Map<String, PartDefinition> parts = new java.util.HashMap<>();
        parts.put("", mesh.getRoot());
        part(parts, "body", "", CubeListBuilder.create().texOffs(22, 38).addBox(-1.5F, -5F, -10F, 3F, 10F, 2F).texOffs(44, 38).addBox(-0.5F, -6F, -10F, 1F, 1F, 2F).texOffs(50, 38).addBox(-0.5F, 5F, -10F, 1F, 1F, 2F).texOffs(10, 38).addBox(-2F, -6.5F, -8F, 4F, 13F, 2F).texOffs(56, 38).addBox(-1F, -7.5F, -8F, 2F, 1F, 2F).texOffs(0, 54).addBox(-1F, 6.5F, -8F, 2F, 1F, 2F).texOffs(0, 20).addBox(-2F, -8F, -6F, 4F, 16F, 2F).texOffs(8, 54).addBox(-1F, -9F, -6F, 2F, 1F, 2F).texOffs(16, 54).addBox(-1F, 8F, -6F, 2F, 1F, 2F).texOffs(14, 0).addBox(-2.5F, -8.5F, -4F, 5F, 17F, 2F).texOffs(24, 54).addBox(-1.5F, -9.5F, -4F, 3F, 1F, 2F).texOffs(34, 54).addBox(-1.5F, 8.5F, -4F, 3F, 1F, 2F).texOffs(0, 0).addBox(-2.5F, -9F, -2F, 5F, 18F, 2F).texOffs(44, 54).addBox(-1.5F, -10F, -2F, 3F, 1F, 2F).texOffs(54, 54).addBox(-1.5F, 9F, -2F, 3F, 1F, 2F).texOffs(28, 0).addBox(-2.5F, -8.5F, 0F, 5F, 17F, 2F).texOffs(0, 57).addBox(-1.5F, -9.5F, 0F, 3F, 1F, 2F).texOffs(10, 57).addBox(-1.5F, 8.5F, 0F, 3F, 1F, 2F).texOffs(42, 0).addBox(-2.5F, -8.5F, 2F, 5F, 17F, 2F).texOffs(20, 57).addBox(-1.5F, -9.5F, 2F, 3F, 1F, 2F).texOffs(30, 57).addBox(-1.5F, 8.5F, 2F, 3F, 1F, 2F).texOffs(12, 20).addBox(-2F, -8F, 4F, 4F, 16F, 2F).texOffs(40, 57).addBox(-1F, -9F, 4F, 2F, 1F, 2F).texOffs(48, 57).addBox(-1F, 8F, 4F, 2F, 1F, 2F).texOffs(24, 20).addBox(-2F, -7.5F, 6F, 4F, 15F, 2F).texOffs(56, 57).addBox(-1F, -8.5F, 6F, 2F, 1F, 2F).texOffs(0, 60).addBox(-1F, 7.5F, 6F, 2F, 1F, 2F).texOffs(36, 20).addBox(-2F, -7F, 8F, 4F, 14F, 2F).texOffs(8, 60).addBox(-1F, -8F, 8F, 2F, 1F, 2F).texOffs(16, 60).addBox(-1F, 7F, 8F, 2F, 1F, 2F).texOffs(24, 60).addBox(-1F, -1F, -11F, 2F, 2F, 1F), PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        part(parts, "dorsal_fin", "body", CubeListBuilder.create().texOffs(48, 20).addBox(0F, -11F, 0F, 0F, 11F, 5F, new CubeDeformation(0.15F, 0F, 0F)), PartPose.offsetAndRotation(0F, -9F, 3F, 0F, 0F, 0F));
        part(parts, "anal_fin", "body", CubeListBuilder.create().texOffs(0, 38).addBox(0F, 0F, 0F, 0F, 11F, 5F, new CubeDeformation(0.15F, 0F, 0F)), PartPose.offsetAndRotation(0F, 9F, 3F, 0F, 0F, 0F));
        part(parts, "tail", "body", CubeListBuilder.create().texOffs(56, 0).addBox(0F, -8F, 0F, 0F, 16F, 3F, new CubeDeformation(0.15F, 0F, 0F)), PartPose.offsetAndRotation(0F, 0F, 10F, 0F, 0F, 0F));
        part(parts, "right_fin", "body", CubeListBuilder.create().texOffs(32, 38).addBox(0F, -1.5F, 0F, 0F, 3F, 3F, new CubeDeformation(0.15F, 0F, 0F)), PartPose.offsetAndRotation(-2.5F, 0F, -4F, 0F, -0.5F, 0F));
        part(parts, "left_fin", "body", CubeListBuilder.create().texOffs(38, 38).addBox(0F, -1.5F, 0F, 0F, 3F, 3F, new CubeDeformation(0.15F, 0F, 0F)), PartPose.offsetAndRotation(2.5F, 0F, -4F, 0F, 0.5F, 0F));
        return LayerDefinition.create(mesh, 64, 64).bakeRoot();
    }

    /** basking_shark: 2.6 blocks long, 59.0 px in the model. */
    static ModelPart baskingShark() {
        MeshDefinition mesh = new MeshDefinition();
        java.util.Map<String, PartDefinition> parts = new java.util.HashMap<>();
        parts.put("", mesh.getRoot());
        part(parts, "body", "", CubeListBuilder.create().texOffs(108, 57).addBox(-3F, -2F, -24F, 6F, 4F, 2F).texOffs(0, 70).addBox(-2F, -3F, -24F, 4F, 1F, 2F).texOffs(12, 70).addBox(-2F, 2F, -24F, 4F, 1F, 2F).texOffs(96, 49).addBox(-4F, -2.5F, -22F, 8F, 5F, 2F).texOffs(24, 70).addBox(-3F, -3.5F, -22F, 6F, 1F, 2F).texOffs(40, 70).addBox(-3F, 2.5F, -22F, 6F, 1F, 2F).texOffs(0, 57).addBox(-4.5F, -2.5F, -20F, 9F, 5F, 2F).texOffs(56, 70).addBox(-2.5F, -4.5F, -20F, 5F, 1F, 2F).texOffs(70, 70).addBox(-2.5F, 3.5F, -20F, 5F, 1F, 2F).texOffs(84, 70).addBox(-3.5F, -3.5F, -20F, 7F, 1F, 2F).texOffs(102, 70).addBox(-3.5F, 2.5F, -20F, 7F, 1F, 2F).texOffs(22, 57).addBox(-5F, -2.5F, -18F, 10F, 5F, 2F).texOffs(0, 73).addBox(-3F, -4.5F, -18F, 6F, 1F, 2F).texOffs(16, 73).addBox(-3F, 3.5F, -18F, 6F, 1F, 2F).texOffs(32, 73).addBox(-4F, -3.5F, -18F, 8F, 1F, 2F).texOffs(52, 73).addBox(-4F, 2.5F, -18F, 8F, 1F, 2F).texOffs(0, 49).addBox(-5F, -3F, -16F, 10F, 6F, 2F).texOffs(72, 73).addBox(-3F, -5F, -16F, 6F, 1F, 2F).texOffs(88, 73).addBox(-3F, 4F, -16F, 6F, 1F, 2F).texOffs(104, 73).addBox(-4F, -4F, -16F, 8F, 1F, 2F).texOffs(0, 76).addBox(-4F, 3F, -16F, 8F, 1F, 2F).texOffs(36, 0).addBox(-5.5F, -3.5F, -14F, 11F, 7F, 2F).texOffs(20, 76).addBox(-3.5F, -5.5F, -14F, 7F, 1F, 2F).texOffs(38, 76).addBox(-3.5F, 4.5F, -14F, 7F, 1F, 2F).texOffs(56, 76).addBox(-4.5F, -4.5F, -14F, 9F, 1F, 2F).texOffs(78, 76).addBox(-4.5F, 3.5F, -14F, 9F, 1F, 2F).texOffs(0, 31).addBox(-5.5F, -3.5F, -12F, 11F, 7F, 2F).texOffs(100, 76).addBox(-3.5F, -5.5F, -12F, 7F, 1F, 2F).texOffs(0, 79).addBox(-3.5F, 4.5F, -12F, 7F, 1F, 2F).texOffs(18, 79).addBox(-4.5F, -4.5F, -12F, 9F, 1F, 2F).texOffs(40, 79).addBox(-4.5F, 3.5F, -12F, 9F, 1F, 2F).texOffs(26, 31).addBox(-5.5F, -3.5F, -10F, 11F, 7F, 2F).texOffs(62, 79).addBox(-3.5F, -5.5F, -10F, 7F, 1F, 2F).texOffs(80, 79).addBox(-3.5F, 4.5F, -10F, 7F, 1F, 2F).texOffs(98, 79).addBox(-4.5F, -4.5F, -10F, 9F, 1F, 2F).texOffs(0, 82).addBox(-4.5F, 3.5F, -10F, 9F, 1F, 2F).texOffs(0, 40).addBox(-5.5F, -3.5F, -8F, 11F, 7F, 2F).texOffs(22, 82).addBox(-3.5F, -5.5F, -8F, 7F, 1F, 2F).texOffs(40, 82).addBox(-3.5F, 4.5F, -8F, 7F, 1F, 2F).texOffs(58, 82).addBox(-4.5F, -4.5F, -8F, 9F, 1F, 2F).texOffs(80, 82).addBox(-4.5F, 3.5F, -8F, 9F, 1F, 2F).texOffs(22, 0).addBox(0F, -13.5F, -8F, 0F, 10F, 7F, new CubeDeformation(0.15F, 0F, 0F)).texOffs(26, 40).addBox(-13F, 3F, -8F, 26F, 1F, 8F).texOffs(94, 40).addBox(-4.5F, -3F, -25F, 9F, 7F, 2F), PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        part(parts, "mid", "body", CubeListBuilder.create().texOffs(24, 49).addBox(-5F, -3F, 0F, 10F, 6F, 2F).texOffs(102, 82).addBox(-3F, -5F, 0F, 6F, 1F, 2F).texOffs(0, 85).addBox(-3F, 4F, 0F, 6F, 1F, 2F).texOffs(16, 85).addBox(-4F, -4F, 0F, 8F, 1F, 2F).texOffs(36, 85).addBox(-4F, 3F, 0F, 8F, 1F, 2F).texOffs(48, 49).addBox(-5F, -3F, 2F, 10F, 6F, 2F).texOffs(56, 85).addBox(-3F, -5F, 2F, 6F, 1F, 2F).texOffs(72, 85).addBox(-3F, 4F, 2F, 6F, 1F, 2F).texOffs(88, 85).addBox(-4F, -4F, 2F, 8F, 1F, 2F).texOffs(108, 85).addBox(-4F, 3F, 2F, 8F, 1F, 2F).texOffs(72, 49).addBox(-5F, -3F, 4F, 10F, 6F, 2F).texOffs(0, 88).addBox(-3F, -5F, 4F, 6F, 1F, 2F).texOffs(16, 88).addBox(-3F, 4F, 4F, 6F, 1F, 2F).texOffs(32, 88).addBox(-4F, -4F, 4F, 8F, 1F, 2F).texOffs(52, 88).addBox(-4F, 3F, 4F, 8F, 1F, 2F).texOffs(46, 57).addBox(-4.5F, -2.5F, 6F, 9F, 5F, 2F).texOffs(72, 88).addBox(-2.5F, -4.5F, 6F, 5F, 1F, 2F).texOffs(86, 88).addBox(-2.5F, 3.5F, 6F, 5F, 1F, 2F).texOffs(100, 88).addBox(-3.5F, -3.5F, 6F, 7F, 1F, 2F).texOffs(0, 91).addBox(-3.5F, 2.5F, 6F, 7F, 1F, 2F).texOffs(68, 57).addBox(-4.5F, -2.5F, 8F, 9F, 5F, 2F).texOffs(18, 91).addBox(-2.5F, -4.5F, 8F, 5F, 1F, 2F).texOffs(32, 91).addBox(-2.5F, 3.5F, 8F, 5F, 1F, 2F).texOffs(46, 91).addBox(-3.5F, -3.5F, 8F, 7F, 1F, 2F).texOffs(64, 91).addBox(-3.5F, 2.5F, 8F, 7F, 1F, 2F).texOffs(0, 64).addBox(-4F, -2F, 10F, 8F, 4F, 2F).texOffs(82, 91).addBox(-2F, -4F, 10F, 4F, 1F, 2F).texOffs(94, 91).addBox(-2F, 3F, 10F, 4F, 1F, 2F).texOffs(106, 91).addBox(-3F, -3F, 10F, 6F, 1F, 2F).texOffs(0, 94).addBox(-3F, 2F, 10F, 6F, 1F, 2F).texOffs(20, 64).addBox(-4F, -2F, 12F, 8F, 4F, 2F).texOffs(16, 94).addBox(-2F, -4F, 12F, 4F, 1F, 2F).texOffs(28, 94).addBox(-2F, 3F, 12F, 4F, 1F, 2F).texOffs(40, 94).addBox(-3F, -3F, 12F, 6F, 1F, 2F).texOffs(56, 94).addBox(-3F, 2F, 12F, 6F, 1F, 2F), PartPose.offsetAndRotation(0F, 0F, -6F, 0F, 0F, 0F));
        part(parts, "rear", "mid", CubeListBuilder.create().texOffs(90, 57).addBox(-3.5F, -2.5F, 0F, 7F, 5F, 2F).texOffs(72, 94).addBox(-2.5F, -3.5F, 0F, 5F, 1F, 2F).texOffs(86, 94).addBox(-2.5F, 2.5F, 0F, 5F, 1F, 2F).texOffs(40, 64).addBox(-3F, -2F, 2F, 6F, 4F, 2F).texOffs(100, 94).addBox(-2F, -3F, 2F, 4F, 1F, 2F).texOffs(112, 94).addBox(-2F, 2F, 2F, 4F, 1F, 2F).texOffs(56, 64).addBox(-3F, -2F, 4F, 6F, 4F, 2F).texOffs(0, 97).addBox(-2F, -3F, 4F, 4F, 1F, 2F).texOffs(12, 97).addBox(-2F, 2F, 4F, 4F, 1F, 2F).texOffs(72, 64).addBox(-2.5F, -1.5F, 6F, 5F, 3F, 2F).texOffs(24, 97).addBox(-1.5F, -2.5F, 6F, 3F, 1F, 2F).texOffs(34, 97).addBox(-1.5F, 1.5F, 6F, 3F, 1F, 2F).texOffs(96, 64).addBox(-2F, -1F, 8F, 4F, 2F, 2F).texOffs(44, 97).addBox(-1F, -2F, 8F, 2F, 1F, 2F).texOffs(52, 97).addBox(-1F, 1F, 8F, 2F, 1F, 2F).texOffs(108, 64).addBox(-2F, -1F, 10F, 4F, 2F, 2F).texOffs(60, 97).addBox(-1F, -2F, 10F, 2F, 1F, 2F).texOffs(68, 97).addBox(-1F, 1F, 10F, 2F, 1F, 2F).texOffs(86, 64).addBox(-1.5F, -1.5F, 12F, 3F, 3F, 2F).texOffs(120, 64).addBox(-1F, -1F, 14F, 2F, 2F, 2F), PartPose.offsetAndRotation(0F, 0F, 14F, 0F, 0F, 0F));
        part(parts, "tail", "rear", CubeListBuilder.create().texOffs(0, 0).addBox(0F, -10F, -1F, 0F, 20F, 11F, new CubeDeformation(0.15F, 0F, 0F)), PartPose.offsetAndRotation(0F, 0F, 16F, 0F, 0F, 0F));
        return LayerDefinition.create(mesh, 128, 128).bakeRoot();
    }

    /** hermit_crab: 0.32 blocks long, 12.5 px in the model. */
    static ModelPart hermitCrab() {
        MeshDefinition mesh = new MeshDefinition();
        java.util.Map<String, PartDefinition> parts = new java.util.HashMap<>();
        parts.put("", mesh.getRoot());
        part(parts, "body", "", CubeListBuilder.create().texOffs(0, 0).addBox(-3.5F, -6F, -2.5F, 7F, 3F, 7F).texOffs(28, 0).addBox(-3F, -9F, -1F, 6F, 3F, 6F).texOffs(0, 10).addBox(-2F, -11F, 1F, 4F, 2F, 4F).texOffs(16, 16).addBox(-1F, -12F, 3F, 2F, 1F, 2F).texOffs(40, 10).addBox(-3F, -4F, -2.5F, 6F, 3F, 1F).texOffs(28, 10).addBox(-2F, -4F, -4.5F, 4F, 3F, 2F).texOffs(24, 16).addBox(-1.5F, -6F, -4.5F, 1F, 2F, 1F).texOffs(28, 16).addBox(0.5F, -6F, -4.5F, 1F, 2F, 1F), PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        part(parts, "right_claw", "body", CubeListBuilder.create().texOffs(16, 10).addBox(-2F, -1.5F, -3F, 3F, 3F, 3F), PartPose.offsetAndRotation(-1.5F, -2.5F, -4.5F, 0F, 0F, 0F));
        part(parts, "left_claw", "body", CubeListBuilder.create().texOffs(54, 10).addBox(0F, -1F, -2F, 2F, 2F, 2F), PartPose.offsetAndRotation(1.5F, -2.5F, -4.5F, 0F, 0F, 0F));
        part(parts, "right_leg0", "body", CubeListBuilder.create().texOffs(0, 16).addBox(-0.5F, 0F, -0.5F, 1F, 3F, 1F), PartPose.offsetAndRotation(-2F, -2F, -3.5F, 0F, -0.4F, 0.7F));
        part(parts, "right_leg1", "body", CubeListBuilder.create().texOffs(4, 16).addBox(-0.5F, 0F, -0.5F, 1F, 3F, 1F), PartPose.offsetAndRotation(-2F, -2F, -2F, 0F, -0.05F, 0.7F));
        part(parts, "left_leg0", "body", CubeListBuilder.create().texOffs(8, 16).addBox(-0.5F, 0F, -0.5F, 1F, 3F, 1F), PartPose.offsetAndRotation(2F, -2F, -3.5F, 0F, 0.4F, -0.7F));
        part(parts, "left_leg1", "body", CubeListBuilder.create().texOffs(12, 16).addBox(-0.5F, 0F, -0.5F, 1F, 3F, 1F), PartPose.offsetAndRotation(2F, -2F, -2F, 0F, 0.05F, -0.7F));
        return LayerDefinition.create(mesh, 64, 32).bakeRoot();
    }

    /** crab: 0.36 blocks long, 14.5 px in the model. */
    static ModelPart crab() {
        MeshDefinition mesh = new MeshDefinition();
        java.util.Map<String, PartDefinition> parts = new java.util.HashMap<>();
        parts.put("", mesh.getRoot());
        part(parts, "body", "", CubeListBuilder.create().texOffs(0, 0).addBox(-5F, -6F, -3.5F, 10F, 2F, 7F).texOffs(0, 9).addBox(-4F, -7F, -2.5F, 8F, 1F, 5F).texOffs(34, 0).addBox(-4F, -4F, -3F, 8F, 1F, 6F).texOffs(40, 15).addBox(-2F, -8F, -3.5F, 1F, 2F, 1F).texOffs(44, 15).addBox(1F, -8F, -3.5F, 1F, 2F, 1F), PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        part(parts, "right_claw", "body", CubeListBuilder.create().texOffs(24, 15).addBox(-1F, -0.5F, -3F, 1F, 1F, 3F).texOffs(0, 15).addBox(-2F, -1.5F, -6F, 3F, 2F, 3F).texOffs(48, 15).addBox(-1.5F, -0.5F, -7.5F, 1F, 1F, 2F).texOffs(60, 15).addBox(-0.5F, -1F, -7F, 1F, 1F, 1F), PartPose.offsetAndRotation(-3.5F, -5F, -3.5F, 0F, -0.35F, 0F));
        part(parts, "right_leg0", "body", CubeListBuilder.create().texOffs(26, 9).addBox(-0.5F, 0F, -0.5F, 1F, 5F, 1F), PartPose.offsetAndRotation(-4.5F, -4.5F, -2F, 0F, -0.4F, 0.9F));
        part(parts, "right_leg1", "body", CubeListBuilder.create().texOffs(30, 9).addBox(-0.5F, 0F, -0.5F, 1F, 5F, 1F), PartPose.offsetAndRotation(-4.5F, -4.5F, -0.5F, 0F, -0.05F, 0.9F));
        part(parts, "right_leg2", "body", CubeListBuilder.create().texOffs(34, 9).addBox(-0.5F, 0F, -0.5F, 1F, 5F, 1F), PartPose.offsetAndRotation(-4.5F, -4.5F, 1F, 0F, 0.3F, 0.9F));
        part(parts, "right_leg3", "body", CubeListBuilder.create().texOffs(38, 9).addBox(-0.5F, 0F, -0.5F, 1F, 5F, 1F), PartPose.offsetAndRotation(-4.5F, -4.5F, 2.5F, 0F, 0.65F, 0.9F));
        part(parts, "left_claw", "body", CubeListBuilder.create().texOffs(32, 15).addBox(0F, -0.5F, -3F, 1F, 1F, 3F).texOffs(12, 15).addBox(-1F, -1.5F, -6F, 3F, 2F, 3F).texOffs(54, 15).addBox(-0.5F, -0.5F, -7.5F, 1F, 1F, 2F).texOffs(0, 20).addBox(0.5F, -1F, -7F, 1F, 1F, 1F), PartPose.offsetAndRotation(3.5F, -5F, -3.5F, 0F, 0.35F, 0F));
        part(parts, "left_leg0", "body", CubeListBuilder.create().texOffs(42, 9).addBox(-0.5F, 0F, -0.5F, 1F, 5F, 1F), PartPose.offsetAndRotation(4.5F, -4.5F, -2F, 0F, 0.4F, -0.9F));
        part(parts, "left_leg1", "body", CubeListBuilder.create().texOffs(46, 9).addBox(-0.5F, 0F, -0.5F, 1F, 5F, 1F), PartPose.offsetAndRotation(4.5F, -4.5F, -0.5F, 0F, 0.05F, -0.9F));
        part(parts, "left_leg2", "body", CubeListBuilder.create().texOffs(50, 9).addBox(-0.5F, 0F, -0.5F, 1F, 5F, 1F), PartPose.offsetAndRotation(4.5F, -4.5F, 1F, 0F, -0.3F, -0.9F));
        part(parts, "left_leg3", "body", CubeListBuilder.create().texOffs(54, 9).addBox(-0.5F, 0F, -0.5F, 1F, 5F, 1F), PartPose.offsetAndRotation(4.5F, -4.5F, 2.5F, 0F, -0.65F, -0.9F));
        return LayerDefinition.create(mesh, 64, 32).bakeRoot();
    }

    /** stingray: 1 blocks long, 30.0 px in the model. */
    static ModelPart stingray() {
        MeshDefinition mesh = new MeshDefinition();
        java.util.Map<String, PartDefinition> parts = new java.util.HashMap<>();
        parts.put("", mesh.getRoot());
        part(parts, "body", "", CubeListBuilder.create().texOffs(18, 56).addBox(-3F, -0.5F, -7F, 6F, 1F, 2F).texOffs(0, 52).addBox(-3.5F, -1F, -5F, 7F, 2F, 2F).texOffs(18, 52).addBox(-4F, -1F, -3F, 8F, 2F, 2F).texOffs(38, 52).addBox(-4F, -1F, -1F, 8F, 2F, 2F).texOffs(0, 56).addBox(-3.5F, -1F, 1F, 7F, 2F, 2F).texOffs(34, 56).addBox(-3F, -0.5F, 3F, 6F, 1F, 2F).texOffs(50, 56).addBox(-2F, -0.5F, 5F, 4F, 1F, 2F), PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        part(parts, "right_wing", "body", CubeListBuilder.create().texOffs(34, 0).addBox(-2F, -0.5F, -6F, 2F, 1F, 12F).texOffs(28, 17).addBox(-4F, -0.5F, -5.5F, 2F, 1F, 11F), PartPose.offsetAndRotation(-3.5F, 0F, -1F, 0F, 0F, 0F));
        part(parts, "right_wing_tip", "right_wing", CubeListBuilder.create().texOffs(26, 30).addBox(-2F, -0.5F, -4.5F, 2F, 1F, 9F).texOffs(22, 42).addBox(-4F, -0.5F, -3F, 2F, 1F, 6F), PartPose.offsetAndRotation(-4F, 0F, 0F, 0F, 0F, 0F));
        part(parts, "left_wing", "body", CubeListBuilder.create().texOffs(0, 17).addBox(0F, -0.5F, -6F, 2F, 1F, 12F).texOffs(0, 30).addBox(2F, -0.5F, -5.5F, 2F, 1F, 11F), PartPose.offsetAndRotation(3.5F, 0F, -1F, 0F, 0F, 0F));
        part(parts, "left_wing_tip", "left_wing", CubeListBuilder.create().texOffs(0, 42).addBox(0F, -0.5F, -4.5F, 2F, 1F, 9F).texOffs(38, 42).addBox(2F, -0.5F, -3F, 2F, 1F, 6F), PartPose.offsetAndRotation(4F, 0F, 0F, 0F, 0F, 0F));
        part(parts, "tail", "body", CubeListBuilder.create().texOffs(0, 0).addBox(-0.5F, -0.5F, 0F, 1F, 1F, 16F).texOffs(0, 60).addBox(-0.5F, -1.5F, 6F, 1F, 1F, 2F), PartPose.offsetAndRotation(0F, 0F, 7F, 0F, 0F, 0F));
        return LayerDefinition.create(mesh, 64, 64).bakeRoot();
    }

    /** octopus: 0.55 blocks long, 15.156146471250732 px in the model. */
    static ModelPart octopus() {
        MeshDefinition mesh = new MeshDefinition();
        java.util.Map<String, PartDefinition> parts = new java.util.HashMap<>();
        parts.put("", mesh.getRoot());
        part(parts, "body", "", CubeListBuilder.create().texOffs(28, 0).addBox(-3F, -9F, -2F, 6F, 3F, 6F).texOffs(0, 0).addBox(-3.5F, -12F, -1.5F, 7F, 3F, 7F).texOffs(22, 10).addBox(-2.5F, -14F, 0.5F, 5F, 2F, 5F).texOffs(0, 10).addBox(-3F, -6F, -3F, 6F, 3F, 5F).texOffs(26, 36).addBox(-3.5F, -7F, -2F, 1F, 2F, 2F).texOffs(32, 36).addBox(2.5F, -7F, -2F, 1F, 2F, 2F), PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        part(parts, "tentacle0", "body", CubeListBuilder.create().texOffs(42, 10).addBox(-1F, 0F, 0F, 2F, 1F, 5F), PartPose.offsetAndRotation(0.437073F, -3F, 2.15615F, 0.55F, 0.2F, 0F));
        part(parts, "tip0", "tentacle0", CubeListBuilder.create().texOffs(0, 18).addBox(-0.5F, 0F, 0F, 1F, 1F, 5F), PartPose.offsetAndRotation(0F, 0F, 5F, -0.4F, 0.3F, 0F));
        part(parts, "tentacle1", "body", CubeListBuilder.create().texOffs(12, 18).addBox(-1F, 0F, 0F, 2F, 1F, 5F), PartPose.offsetAndRotation(1.83368F, -3F, 1.21557F, 0.55F, 0.985398F, 0F));
        part(parts, "tip1", "tentacle1", CubeListBuilder.create().texOffs(26, 18).addBox(-0.5F, 0F, 0F, 1F, 1F, 5F), PartPose.offsetAndRotation(0F, 0F, 5F, -0.4F, 0.3F, 0F));
        part(parts, "tentacle2", "body", CubeListBuilder.create().texOffs(38, 18).addBox(-1F, 0F, 0F, 2F, 1F, 5F), PartPose.offsetAndRotation(2.15615F, -3F, -0.437073F, 0.55F, 1.7708F, 0F));
        part(parts, "tip2", "tentacle2", CubeListBuilder.create().texOffs(52, 18).addBox(-0.5F, 0F, 0F, 1F, 1F, 5F), PartPose.offsetAndRotation(0F, 0F, 5F, -0.4F, 0.3F, 0F));
        part(parts, "tentacle3", "body", CubeListBuilder.create().texOffs(0, 24).addBox(-1F, 0F, 0F, 2F, 1F, 5F), PartPose.offsetAndRotation(1.21557F, -3F, -1.83368F, 0.55F, 2.55619F, 0F));
        part(parts, "tip3", "tentacle3", CubeListBuilder.create().texOffs(14, 24).addBox(-0.5F, 0F, 0F, 1F, 1F, 5F), PartPose.offsetAndRotation(0F, 0F, 5F, -0.4F, 0.3F, 0F));
        part(parts, "tentacle4", "body", CubeListBuilder.create().texOffs(26, 24).addBox(-1F, 0F, 0F, 2F, 1F, 5F), PartPose.offsetAndRotation(-0.437073F, -3F, -2.15615F, 0.55F, 3.34159F, 0F));
        part(parts, "tip4", "tentacle4", CubeListBuilder.create().texOffs(40, 24).addBox(-0.5F, 0F, 0F, 1F, 1F, 5F), PartPose.offsetAndRotation(0F, 0F, 5F, -0.4F, 0.3F, 0F));
        part(parts, "tentacle5", "body", CubeListBuilder.create().texOffs(0, 30).addBox(-1F, 0F, 0F, 2F, 1F, 5F), PartPose.offsetAndRotation(-1.83368F, -3F, -1.21557F, 0.55F, 4.12699F, 0F));
        part(parts, "tip5", "tentacle5", CubeListBuilder.create().texOffs(14, 30).addBox(-0.5F, 0F, 0F, 1F, 1F, 5F), PartPose.offsetAndRotation(0F, 0F, 5F, -0.4F, 0.3F, 0F));
        part(parts, "tentacle6", "body", CubeListBuilder.create().texOffs(26, 30).addBox(-1F, 0F, 0F, 2F, 1F, 5F), PartPose.offsetAndRotation(-2.15615F, -3F, 0.437073F, 0.55F, 4.91239F, 0F));
        part(parts, "tip6", "tentacle6", CubeListBuilder.create().texOffs(40, 30).addBox(-0.5F, 0F, 0F, 1F, 1F, 5F), PartPose.offsetAndRotation(0F, 0F, 5F, -0.4F, 0.3F, 0F));
        part(parts, "tentacle7", "body", CubeListBuilder.create().texOffs(0, 36).addBox(-1F, 0F, 0F, 2F, 1F, 5F), PartPose.offsetAndRotation(-1.21557F, -3F, 1.83368F, 0.55F, 5.69779F, 0F));
        part(parts, "tip7", "tentacle7", CubeListBuilder.create().texOffs(14, 36).addBox(-0.5F, 0F, 0F, 1F, 1F, 5F), PartPose.offsetAndRotation(0F, 0F, 5F, -0.4F, 0.3F, 0F));
        return LayerDefinition.create(mesh, 64, 64).bakeRoot();
    }

    /** lobster: 0.65 blocks long, 34.0 px in the model. */
    static ModelPart lobster() {
        MeshDefinition mesh = new MeshDefinition();
        java.util.Map<String, PartDefinition> parts = new java.util.HashMap<>();
        parts.put("", mesh.getRoot());
        part(parts, "body", "", CubeListBuilder.create().texOffs(0, 15).addBox(-2F, -5F, -5F, 4F, 3F, 6F).texOffs(48, 15).addBox(-1.5F, -5F, -8F, 3F, 2F, 3F).texOffs(12, 33).addBox(-1.5F, -6F, -7F, 1F, 1F, 1F).texOffs(16, 33).addBox(0.5F, -6F, -7F, 1F, 1F, 1F).texOffs(0, 0).addBox(-1F, -5.5F, -22F, 0F, 1F, 14F, new CubeDeformation(0.15F, 0F, 0F)).texOffs(28, 0).addBox(1F, -5.5F, -22F, 0F, 1F, 14F, new CubeDeformation(0.15F, 0F, 0F)), PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        part(parts, "tail", "body", CubeListBuilder.create().texOffs(52, 24).addBox(-2F, -1.5F, 0F, 4F, 2F, 2F).texOffs(0, 29).addBox(-2F, -1.3F, 1.6F, 4F, 2F, 2F).texOffs(12, 29).addBox(-2F, -1.1F, 3.2F, 4F, 2F, 2F).texOffs(24, 29).addBox(-1.5F, -0.9F, 4.8F, 3F, 2F, 2F).texOffs(34, 29).addBox(-1.5F, -0.7F, 6.4F, 3F, 2F, 2F).texOffs(44, 29).addBox(-3F, -0.5F, 8F, 6F, 1F, 3F), PartPose.offsetAndRotation(0F, -3.5F, 1F, 0F, 0F, 0F));
        part(parts, "right_claw", "body", CubeListBuilder.create().texOffs(0, 24).addBox(-1F, -0.5F, -4F, 1F, 1F, 4F).texOffs(20, 15).addBox(-2F, -1.5F, -8F, 3F, 2F, 4F).texOffs(0, 33).addBox(-1F, -0.5F, -10F, 1F, 1F, 2F), PartPose.offsetAndRotation(-2F, -3.5F, -6F, 0F, -0.3F, 0F));
        part(parts, "right_leg0", "body", CubeListBuilder.create().texOffs(10, 24).addBox(-0.5F, 0F, -0.5F, 1F, 4F, 1F), PartPose.offsetAndRotation(-2F, -2.5F, -4F, 0F, -0.4F, 0.85F));
        part(parts, "right_leg1", "body", CubeListBuilder.create().texOffs(14, 24).addBox(-0.5F, 0F, -0.5F, 1F, 4F, 1F), PartPose.offsetAndRotation(-2F, -2.5F, -2.6F, 0F, -0.05F, 0.85F));
        part(parts, "right_leg2", "body", CubeListBuilder.create().texOffs(18, 24).addBox(-0.5F, 0F, -0.5F, 1F, 4F, 1F), PartPose.offsetAndRotation(-2F, -2.5F, -1.2F, 0F, 0.3F, 0.85F));
        part(parts, "right_leg3", "body", CubeListBuilder.create().texOffs(22, 24).addBox(-0.5F, 0F, -0.5F, 1F, 4F, 1F), PartPose.offsetAndRotation(-2F, -2.5F, 0.2F, 0F, 0.65F, 0.85F));
        part(parts, "left_claw", "body", CubeListBuilder.create().texOffs(26, 24).addBox(0F, -0.5F, -4F, 1F, 1F, 4F).texOffs(34, 15).addBox(-1F, -1.5F, -8F, 3F, 2F, 4F).texOffs(6, 33).addBox(0F, -0.5F, -10F, 1F, 1F, 2F), PartPose.offsetAndRotation(2F, -3.5F, -6F, 0F, 0.3F, 0F));
        part(parts, "left_leg0", "body", CubeListBuilder.create().texOffs(36, 24).addBox(-0.5F, 0F, -0.5F, 1F, 4F, 1F), PartPose.offsetAndRotation(2F, -2.5F, -4F, 0F, 0.4F, -0.85F));
        part(parts, "left_leg1", "body", CubeListBuilder.create().texOffs(40, 24).addBox(-0.5F, 0F, -0.5F, 1F, 4F, 1F), PartPose.offsetAndRotation(2F, -2.5F, -2.6F, 0F, 0.05F, -0.85F));
        part(parts, "left_leg2", "body", CubeListBuilder.create().texOffs(44, 24).addBox(-0.5F, 0F, -0.5F, 1F, 4F, 1F), PartPose.offsetAndRotation(2F, -2.5F, -1.2F, 0F, -0.3F, -0.85F));
        part(parts, "left_leg3", "body", CubeListBuilder.create().texOffs(48, 24).addBox(-0.5F, 0F, -0.5F, 1F, 4F, 1F), PartPose.offsetAndRotation(2F, -2.5F, 0.2F, 0F, -0.65F, -0.85F));
        return LayerDefinition.create(mesh, 64, 64).bakeRoot();
    }

    /** shrimp: 0.24 blocks long, 26.0 px in the model. */
    static ModelPart shrimp() {
        MeshDefinition mesh = new MeshDefinition();
        java.util.Map<String, PartDefinition> parts = new java.util.HashMap<>();
        parts.put("", mesh.getRoot());
        part(parts, "body", "", CubeListBuilder.create().texOffs(40, 0).addBox(-1.5F, -6F, -4F, 3F, 3F, 5F).texOffs(56, 0).addBox(-1F, -6F, -6F, 2F, 2F, 2F).texOffs(46, 11).addBox(-0.5F, -6.5F, -8F, 1F, 1F, 2F).texOffs(0, 0).addBox(-0.5F, -6F, -16F, 0F, 1F, 10F, new CubeDeformation(0.15F, 0F, 0F)).texOffs(20, 0).addBox(0.5F, -6F, -16F, 0F, 1F, 10F, new CubeDeformation(0.15F, 0F, 0F)).texOffs(52, 11).addBox(-1.5F, -3F, -3F, 3F, 3F, 0F, new CubeDeformation(0F, 0F, 0.15F)).texOffs(58, 11).addBox(-1.5F, -3F, -1.8F, 3F, 3F, 0F, new CubeDeformation(0F, 0F, 0.15F)).texOffs(0, 15).addBox(-1.5F, -3F, -0.6F, 3F, 3F, 0F, new CubeDeformation(0F, 0F, 0.15F)).texOffs(6, 15).addBox(-1.5F, -3F, 0.6F, 3F, 3F, 0F, new CubeDeformation(0F, 0F, 0.15F)), PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        part(parts, "tail", "body", CubeListBuilder.create().texOffs(0, 11).addBox(-1F, -1F, 0F, 2F, 2F, 2F).texOffs(8, 11).addBox(-1F, -1F, 1.5F, 2F, 2F, 2F).texOffs(16, 11).addBox(-1F, -1F, 3F, 2F, 2F, 2F).texOffs(24, 11).addBox(-1F, -1F, 4.5F, 2F, 2F, 2F).texOffs(32, 11).addBox(-2F, 0F, 6F, 4F, 1F, 3F), PartPose.offsetAndRotation(0F, -5F, 1F, 0.5F, 0F, 0F));
        return LayerDefinition.create(mesh, 64, 32).bakeRoot();
    }

    /** anchor: 0.43 blocks long, 19.5 px in the model. */
    static ModelPart anchor() {
        MeshDefinition mesh = new MeshDefinition();
        java.util.Map<String, PartDefinition> parts = new java.util.HashMap<>();
        parts.put("", mesh.getRoot());
        part(parts, "body", "", CubeListBuilder.create().texOffs(0, 0).addBox(-1F, -15F, -1F, 2F, 14F, 2F).texOffs(0, 20).addBox(-4F, -13F, -1F, 8F, 1F, 2F).texOffs(32, 0).addBox(-5F, -13.5F, -1.5F, 1F, 2F, 3F).texOffs(40, 0).addBox(4F, -13.5F, -1.5F, 1F, 2F, 3F).texOffs(28, 20).addBox(-2F, -19F, -0.5F, 4F, 1F, 1F).texOffs(38, 20).addBox(-2F, -16F, -0.5F, 4F, 1F, 1F).texOffs(20, 20).addBox(-3F, -18F, -0.5F, 1F, 2F, 1F).texOffs(24, 20).addBox(2F, -18F, -0.5F, 1F, 2F, 1F).texOffs(0, 16).addBox(-2F, -2F, -1F, 4F, 2F, 2F).texOffs(12, 16).addBox(-4F, -3F, -1F, 2F, 2F, 2F).texOffs(20, 16).addBox(2F, -3F, -1F, 2F, 2F, 2F).texOffs(28, 16).addBox(-6F, -4F, -1F, 2F, 2F, 2F).texOffs(36, 16).addBox(4F, -4F, -1F, 2F, 2F, 2F).texOffs(44, 16).addBox(-7F, -6F, -1F, 1F, 2F, 2F).texOffs(50, 16).addBox(6F, -6F, -1F, 1F, 2F, 2F).texOffs(8, 0).addBox(-9F, -9F, -1.5F, 3F, 3F, 3F).texOffs(20, 0).addBox(6F, -9F, -1.5F, 3F, 3F, 3F).texOffs(48, 0).addBox(-1.5F, -1.5F, -1.5F, 3F, 2F, 3F), PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0.35F));
        return LayerDefinition.create(mesh, 64, 32).bakeRoot();
    }

    /** diving_helmet: 0.3 blocks long, 16.0 px in the model. */
    static ModelPart divingHelmet() {
        MeshDefinition mesh = new MeshDefinition();
        java.util.Map<String, PartDefinition> parts = new java.util.HashMap<>();
        parts.put("", mesh.getRoot());
        part(parts, "body", "", CubeListBuilder.create().texOffs(0, 0).addBox(-7F, -3F, -7F, 14F, 3F, 14F).texOffs(0, 46).addBox(-6F, -4F, -6F, 12F, 1F, 12F).texOffs(0, 32).addBox(-6F, -6F, -6F, 12F, 2F, 12F).texOffs(0, 17).addBox(-6F, -9F, -6F, 12F, 3F, 12F).texOffs(0, 59).addBox(-5.5F, -11F, -5.5F, 11F, 2F, 11F).texOffs(0, 72).addBox(-4.5F, -12F, -4.5F, 9F, 1F, 9F).texOffs(36, 72).addBox(-3.5F, -13F, -3.5F, 7F, 1F, 7F).texOffs(34, 82).addBox(-2F, -14F, -2F, 4F, 1F, 4F).texOffs(20, 82).addBox(-3F, -10F, -6.5F, 6F, 6F, 1F).texOffs(0, 82).addBox(-6.5F, -9F, -2F, 1F, 4F, 4F).texOffs(10, 82).addBox(5.5F, -9F, -2F, 1F, 4F, 4F).texOffs(50, 82).addBox(-1F, -16F, -1F, 2F, 2F, 2F), PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0.12F));
        return LayerDefinition.create(mesh, 64, 128).bakeRoot();
    }

    /** clam: 0.22 blocks long, 8.0 px in the model. */
    static ModelPart clam() {
        MeshDefinition mesh = new MeshDefinition();
        java.util.Map<String, PartDefinition> parts = new java.util.HashMap<>();
        parts.put("", mesh.getRoot());
        part(parts, "body", "", CubeListBuilder.create().texOffs(0, 0).addBox(-4F, -2F, -4F, 8F, 2F, 7F).texOffs(30, 9).addBox(-3F, -2F, -5F, 6F, 2F, 1F).texOffs(0, 9).addBox(-3F, -3F, -3F, 6F, 1F, 5F).texOffs(22, 9).addBox(-1F, -5F, -1.5F, 2F, 2F, 2F), PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        part(parts, "lid", "body", CubeListBuilder.create().texOffs(30, 0).addBox(-4F, -2F, -7F, 8F, 2F, 7F).texOffs(44, 9).addBox(-3F, -2F, -8F, 6F, 2F, 1F), PartPose.offsetAndRotation(0F, -2F, 3F, -1F, 0F, 0F));
        return LayerDefinition.create(mesh, 64, 32).bakeRoot();
    }

    /** starfish: 0.21 blocks long, 9.0 px in the model. */
    static ModelPart starfish() {
        MeshDefinition mesh = new MeshDefinition();
        java.util.Map<String, PartDefinition> parts = new java.util.HashMap<>();
        parts.put("", mesh.getRoot());
        part(parts, "body", "", CubeListBuilder.create().texOffs(0, 0).addBox(-2F, -2F, -2F, 4F, 2F, 4F), PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        part(parts, "arm0", "body", CubeListBuilder.create().texOffs(16, 0).addBox(-1F, -1F, 1F, 2F, 1F, 4F).texOffs(12, 6).addBox(-0.5F, -1F, 5F, 1F, 1F, 2F), PartPose.offsetAndRotation(0F, -1F, 0F, 0F, 0F, 0F));
        part(parts, "arm1", "body", CubeListBuilder.create().texOffs(28, 0).addBox(-1F, -1F, 1F, 2F, 1F, 4F).texOffs(18, 6).addBox(-0.5F, -1F, 5F, 1F, 1F, 2F), PartPose.offsetAndRotation(0F, -1F, 0F, 0F, 1.25664F, 0F));
        part(parts, "arm2", "body", CubeListBuilder.create().texOffs(40, 0).addBox(-1F, -1F, 1F, 2F, 1F, 4F).texOffs(24, 6).addBox(-0.5F, -1F, 5F, 1F, 1F, 2F), PartPose.offsetAndRotation(0F, -1F, 0F, 0F, 2.51327F, 0F));
        part(parts, "arm3", "body", CubeListBuilder.create().texOffs(52, 0).addBox(-1F, -1F, 1F, 2F, 1F, 4F).texOffs(30, 6).addBox(-0.5F, -1F, 5F, 1F, 1F, 2F), PartPose.offsetAndRotation(0F, -1F, 0F, 0F, 3.76991F, 0F));
        part(parts, "arm4", "body", CubeListBuilder.create().texOffs(0, 6).addBox(-1F, -1F, 1F, 2F, 1F, 4F).texOffs(36, 6).addBox(-0.5F, -1F, 5F, 1F, 1F, 2F), PartPose.offsetAndRotation(0F, -1F, 0F, 0F, 5.02655F, 0F));
        return LayerDefinition.create(mesh, 64, 32).bakeRoot();
    }

    /** scallop: 0.14 blocks long, 9.0 px in the model. */
    static ModelPart scallop() {
        MeshDefinition mesh = new MeshDefinition();
        java.util.Map<String, PartDefinition> parts = new java.util.HashMap<>();
        parts.put("", mesh.getRoot());
        part(parts, "body", "", CubeListBuilder.create().texOffs(0, 0).addBox(-1F, -1F, -4F, 2F, 1F, 2F).texOffs(8, 0).addBox(-2F, -1.25F, -2F, 4F, 1F, 2F).texOffs(20, 0).addBox(-3F, -1.5F, 0F, 6F, 1F, 2F).texOffs(36, 0).addBox(-3.5F, -1.75F, 2F, 7F, 1F, 2F).texOffs(54, 0).addBox(-2F, -1F, -5F, 4F, 1F, 1F), PartPose.offsetAndRotation(0F, 24F, 0F, 0.25F, 0F, 0F));
        return LayerDefinition.create(mesh, 64, 32).bakeRoot();
    }

    /** anemone: 0.25 blocks long, 11.0 px in the model. */
    static ModelPart anemone() {
        MeshDefinition mesh = new MeshDefinition();
        java.util.Map<String, PartDefinition> parts = new java.util.HashMap<>();
        parts.put("", mesh.getRoot());
        part(parts, "body", "", CubeListBuilder.create().texOffs(0, 0).addBox(-2.5F, -4F, -2.5F, 5F, 4F, 5F).texOffs(20, 0).addBox(-3F, -5F, -3F, 6F, 1F, 6F), PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        part(parts, "tentacle0", "body", CubeListBuilder.create().texOffs(44, 0).addBox(-0.5F, -6F, -0.5F, 1F, 6F, 1F), PartPose.offsetAndRotation(1F, -5F, 0F, -0F, 0F, 0.25F));
        part(parts, "tentacle1", "body", CubeListBuilder.create().texOffs(48, 0).addBox(-0.5F, -6F, -0.5F, 1F, 6F, 1F), PartPose.offsetAndRotation(6.12323e-17F, -5F, 1F, -0.25F, 0F, 1.53081e-17F));
        part(parts, "tentacle2", "body", CubeListBuilder.create().texOffs(52, 0).addBox(-0.5F, -6F, -0.5F, 1F, 6F, 1F), PartPose.offsetAndRotation(-1F, -5F, 1.22465e-16F, -3.06162e-17F, 0F, -0.25F));
        part(parts, "tentacle3", "body", CubeListBuilder.create().texOffs(56, 0).addBox(-0.5F, -6F, -0.5F, 1F, 6F, 1F), PartPose.offsetAndRotation(-1.83697e-16F, -5F, -1F, 0.25F, 0F, -4.59243e-17F));
        part(parts, "tentacle4", "body", CubeListBuilder.create().texOffs(60, 0).addBox(-0.5F, -5F, -0.5F, 1F, 5F, 1F), PartPose.offsetAndRotation(2.39476F, -5F, 1.01249F, -0.272593F, 0F, 0.644743F));
        part(parts, "tentacle5", "body", CubeListBuilder.create().texOffs(0, 9).addBox(-0.5F, -5F, -0.5F, 1F, 5F, 1F), PartPose.offsetAndRotation(0.977413F, -5F, 2.40929F, -0.648654F, 0F, 0.26315F));
        part(parts, "tentacle6", "body", CubeListBuilder.create().texOffs(4, 9).addBox(-0.5F, -5F, -0.5F, 1F, 5F, 1F), PartPose.offsetAndRotation(-1.01249F, -5F, 2.39476F, -0.644743F, 0F, -0.272593F));
        part(parts, "tentacle7", "body", CubeListBuilder.create().texOffs(8, 9).addBox(-0.5F, -5F, -0.5F, 1F, 5F, 1F), PartPose.offsetAndRotation(-2.40929F, -5F, 0.977413F, -0.26315F, 0F, -0.648654F));
        part(parts, "tentacle8", "body", CubeListBuilder.create().texOffs(12, 9).addBox(-0.5F, -5F, -0.5F, 1F, 5F, 1F), PartPose.offsetAndRotation(-2.39476F, -5F, -1.01249F, 0.272593F, 0F, -0.644743F));
        part(parts, "tentacle9", "body", CubeListBuilder.create().texOffs(16, 9).addBox(-0.5F, -5F, -0.5F, 1F, 5F, 1F), PartPose.offsetAndRotation(-0.977413F, -5F, -2.40929F, 0.648654F, 0F, -0.26315F));
        part(parts, "tentacle10", "body", CubeListBuilder.create().texOffs(20, 9).addBox(-0.5F, -5F, -0.5F, 1F, 5F, 1F), PartPose.offsetAndRotation(1.01249F, -5F, -2.39476F, 0.644743F, 0F, 0.272593F));
        part(parts, "tentacle11", "body", CubeListBuilder.create().texOffs(24, 9).addBox(-0.5F, -5F, -0.5F, 1F, 5F, 1F), PartPose.offsetAndRotation(2.40929F, -5F, -0.977413F, 0.26315F, 0F, 0.648654F));
        return LayerDefinition.create(mesh, 64, 32).bakeRoot();
    }
}
