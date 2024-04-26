// Defines the precision of floating point numbers. Higher precision means more accruate representation at a bigger cost of performance, and vice versa.
precision mediump float;

// Resolution of the panel, passed in from OCPanelBackgroundGradientRenderer.
uniform vec2 uResolution;
// Base background color of the panel, passed in from OCPanelBackgroundGradientRenderer.
uniform vec4 uBackgroundBaseColor;
// Corner radius of the panel, passed in from OCPanelBackgroundGradientRenderer.
uniform float uCornerRadius;
// Determines what side of the panel should have a rounded corner. Direction number codes: 1 - top, 2 - right, 3 - bottom, 4 - left
uniform int uCornerRadiiDirection;
// Y Offset from the top of the window, passed in from OCPanelBackgroundGradientRenderer.
// This allows us to draw/clip only a portion of the gradient.
// e.g. If I only want to draw the bottom 'xxx' portion
//    +---------------+   -+
//    |               |    |
//    |               |    | y offset
//    |               |    |
//    |---------------|   -+
//    |      xxx      |    | uResolution.y
//    +---------------+   -+
uniform float uYOffset;
// Whether this view is for the control bar. The logic for drawing gradient is different as it is attached to the top of the main panel.
uniform int uIsControlBar;

/**
 * Helper function to apply dithering. The technique used here is a Perlin noise function,
 * which is a common technique for generating random-like values in shaders.
 * @param color - The color to apply dithering for.
 * @return - The color with dithering applied.
 */
vec4 dither(vec4 color) {
    // From "NEXT GENERATION POST PROCESSING IN CALL OF DUTY: ADVANCED WARFARE"
    // http://advances.realtimerendering.com/s2014/index.html
    vec3 magic = vec3(0.06711056, 0.00583715, 52.9829189);
    float ditherIntensity = 2.0;
    float noise = fract(magic.z * fract(dot(gl_FragCoord.xy, magic.xy))) * ditherIntensity;
    vec3 rnd = vec3(noise / 255.0);
    vec3 outColor = color.rgb + rnd;
    return vec4(floor(outColor * 255.0) / 255.0, color.a);
}

/**
 * Helper function to draw corner radius. In the shader world, this mean using the position
 * information of the current pixel and some calculations to determine if this pixel is out of
 * bounds of the corners. If it is, make that pixel transparent.
 * @param color - The color to apply transparency for.
 * @return - The color with transparency applied.
 */
vec4 cornerRadius(vec4 color) {

    // The equation of a circle is (x - h) ^ 2 + (y - k) ^ 2 = r ^ 2, where x and y stand
    // for the current coordinate, h and k stand for the coordinate of center of the circle,
    // and r is the radius. Over here, termX and termY represents (x - h) ^ 2 and (y - k) ^ 2
    // respectively in this equation. They are initialized to -1 to ensure that the pixel we
    // are checking lives in the corner regions that should be checked.
    float termX = -1.0;
    float termY = -1.0;
    // Apply offset to our uResolution.y value
    float uResolutionYWithOffset = uResolution.y + uYOffset;

    // Sets (x - h) ^ 2 if the pixel is close to the left edge of the panel
    if (uCornerRadiiDirection != 2 && gl_FragCoord.x < uCornerRadius) {
        termX = pow(gl_FragCoord.x - uCornerRadius, 2.0);
    }
    // Sets (x - h) ^ 2 if the pixel is close to the right edge of the panel
    if (uCornerRadiiDirection != 4 && gl_FragCoord.x > uResolution.x - uCornerRadius) {
        termX = pow(gl_FragCoord.x - uResolution.x + uCornerRadius, 2.0);
    }
    // Sets (y - k) ^ 2  if the pixel is close to the bottom edge of the panel
    if (uCornerRadiiDirection != 1 && gl_FragCoord.y < uCornerRadius) {
        termY = pow(gl_FragCoord.y - uCornerRadius, 2.0);
    }
    // Sets (y - k) ^ 2  if the pixel is close to the top edge of the panel
    if (uCornerRadiiDirection != 3 && gl_FragCoord.y > uResolutionYWithOffset - uCornerRadius) {
        termY = pow(gl_FragCoord.y - uResolutionYWithOffset + uCornerRadius, 2.0);
    }

    // Used for the final calculation, set this to 0.0 if this pixel is out of the corner bounds
    // (that means it should be transparent).
    float alpha = 1.0;

    vec4 finalColor = color;
    // Checks for the r ^ 2 in that circle equation. If termX and termY combined is greater, then
    // it means the current pixel is out of bounds of the circle, and should be set to transparent.
    if (termX > 0.0 && termY > 0.0 && termX + termY > pow(uCornerRadius, 2.0)) {
        finalColor = vec4(0, 0, 0, 0);
    }

    // Calculate and return the final color.
    return finalColor;
}

/**
 * Helper function to draw a radial gradient. In the shader world, this doesn't mean drawing
 * the entire gradient all at once but just a single pixel of a gradient.
 * @param startColor - The color at the center of the gradient.
 * @param endColor - The color on the edge of the gradient.
 * @param center - Position of the center of the gradient, expressed as a normalized x,y coordinate that spans from 0-1.
 * @param radius - Radius of the gradient, expressed as a vector of the semi-major and semi-minor axes of the ellipse, normalized to 0-1.
 * @return - What the color should be at the current pixel.
 */
vec4 radialGradient(vec4 startColor, vec4 endColor, vec2 center, vec2 radius) {

    // Apply offset to our uResolution.y value
    vec2 uResolutionWithOffset = uResolution;
    uResolutionWithOffset.y += uYOffset;

    // Calculates the normalized position of the current pixel. gl_FragCoord is a global input that represents the position of the current pixel.
    vec2 currentNormalizedPosition = gl_FragCoord.xy / uResolutionWithOffset.xy;

    // Calculates the normalized vector from the gradient center for the current pixel.
    vec2 nomralizedVector = (currentNormalizedPosition - center) / radius;

    // Calculates the distance from the center for the current pixel.
    float distance = length(nomralizedVector);

    // Calculates the gradient intensity, which performs linear interpolation between the gradient bounds based on the pixel distance.
    float gradientIntensity = smoothstep(0.0, 1.0, distance);

    // Calculates the gradient alpha, which is a linear interpolation of the intensity.
    float gradientAlpha = mix(1.0 - startColor.a, 1.0, gradientIntensity);

    // Mix the start and end colors based on the gradient alpha.
    return mix(startColor, endColor, gradientAlpha);
}

/**
 * Entry point of the shader. The idea behaind a shader is a mapping between some global inputs
 * such as time and space, to the color that should be drawn for this specfic pixel. Unlike how
 * you think of drawing on Android/React Native/anything else, you don't draw the entire shape all
 * at once, but rather do so pixel by pixel. The GPU parallelizes the pixel calculations and for
 * each of those pixels this function will be called.
 */
void main() {
    // Stores the final color after mixing the base plus three layers of gradient
    vec4 finalColor = uBackgroundBaseColor;

    // Whether this shader is for a control bar or not
    bool isControlBar = uIsControlBar == 1;

    // Different center and radius if the gradient is a control bar
    vec2 centerFirstGradient = isControlBar ? vec2(0.0, 0.0) : vec2(0.0, 1.0);
    vec2 centerSecondGradient = isControlBar ? vec2(1.0, 0.0) : vec2(1.0, 1.0);
    // TODO: dynamically adjust the radius based on the current panel height
    vec2 radius = isControlBar ? vec2(1.5, 640.0 * 1.25 / uResolution.y) : vec2(1.5, 1.0);

    // First required gradient. Starts from the top-left and has a purple-ish color.
    finalColor = radialGradient(
        // TODO(T101804452): Remove hardcoded gradient colors
        vec4(0.62745098, 0.2, 1.0, 0.07843137),
        finalColor,
        centerFirstGradient,
        radius
    );

    // Second required gradient. Starts from the top-right and has a green-ish color.
    finalColor = radialGradient(
        // TODO(T101804452): Remove hardcoded gradient colors
        vec4(0.14453125, 0.82421875, 0.3984375, 0.0390625),
        finalColor,
        centerSecondGradient,
        radius
    );

    // Third required gradient. Starts from the bottom and has a red-ish color.
    // This gradient is not needed for the control bar.
    if (!isControlBar) {
        finalColor = radialGradient(
            // TODO(T101804452): Remove hardcoded gradient colors
            vec4(1.0, 0.42352941, 0.36078431, 0.07843137),
            finalColor,
            vec2(0.5, 0.0),
            vec2(1.0, 1.0)
        );
    }

    // Applying dithering to the current pixel.
    finalColor = dither(finalColor);

    // Make non-transparent pixels fully opaque.
    finalColor.a = 1.0;

    // Set the color of the current pixel, after applying a corner radius. gl_FragColor is a global output that represents the color of the current pixel.
    gl_FragColor = uCornerRadius > 0.0 ? cornerRadius(finalColor) : finalColor;
}
