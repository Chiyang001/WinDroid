package com.winlator.renderer.material;

public final class BackgroundMaterial extends ScreenMaterial {
    public final Uniform textureInset = new Uniform("textureInset");

    @Override
    protected String getVertexShader() {
        return String.join("\n",
            "in vec2 position;",
            "out vec2 vUV;",
            "uniform vec2 textureInset;",
            "void main() {",
                "vUV = mix(textureInset, vec2(1.0) - textureInset, vec2(position.x, 1.0 - position.y));",
                "gl_Position = vec4(2.0 * position - 1.0, 0.0, 1.0);",
            "}"
        );
    }
}
