export function MapEnvironment() {
  return (
    <>
      {/* Lighting — brighter to avoid dark scene */}
      <ambientLight intensity={0.8} color="#c7d2fe" />
      <directionalLight position={[5, 12, 5]} intensity={1.5} color="#f0f0ff" />
      <directionalLight position={[-4, 8, -4]} intensity={0.6} color="#a5b4fc" />
      <pointLight position={[0, 5, 0]} intensity={0.5} color="#06b6d4" distance={20} />

      {/* Ground plane */}
      <mesh rotation={[-Math.PI / 2, 0, 0]} position={[0, -0.05, 0]} receiveShadow>
        <planeGeometry args={[40, 40]} />
        <meshStandardMaterial color="#141b2d" roughness={0.85} metalness={0.1} />
      </mesh>

      {/* Grid lines — lighter colour */}
      <gridHelper args={[30, 30, '#2d3a5c', '#1e2a4a']} position={[0, 0.01, 0]} />
    </>
  );
}
