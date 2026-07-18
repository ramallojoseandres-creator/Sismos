// Geocercas simplificadas por estado (polígonos rectangulares aproximados).
// Se usan para clasificación rápida de alertas en tiempo real.
const VENEZUELA_STATE_GEOFENCES = [
  { name: "Amazonas", polygon: [[-67.8, 0.6], [-63.5, 0.6], [-63.5, 6.4], [-67.8, 6.4]] },
  { name: "Anzoátegui", polygon: [[-65.9, 8.2], [-63.0, 8.2], [-63.0, 10.8], [-65.9, 10.8]] },
  { name: "Apure", polygon: [[-72.7, 6.0], [-67.8, 6.0], [-67.8, 8.6], [-72.7, 8.6]] },
  { name: "Aragua", polygon: [[-67.9, 9.7], [-66.2, 9.7], [-66.2, 10.7], [-67.9, 10.7]] },
  { name: "Barinas", polygon: [[-71.5, 7.3], [-68.7, 7.3], [-68.7, 9.4], [-71.5, 9.4]] },
  { name: "Bolívar", polygon: [[-66.7, 4.2], [-59.8, 4.2], [-59.8, 9.3], [-66.7, 9.3]] },
  { name: "Carabobo", polygon: [[-68.6, 9.8], [-67.5, 9.8], [-67.5, 10.6], [-68.6, 10.6]] },
  { name: "Cojedes", polygon: [[-69.6, 8.8], [-67.8, 8.8], [-67.8, 10.1], [-69.6, 10.1]] },
  { name: "Delta Amacuro", polygon: [[-62.5, 8.0], [-60.0, 8.0], [-60.0, 10.4], [-62.5, 10.4]] },
  { name: "Distrito Capital", polygon: [[-67.1, 10.4], [-66.7, 10.4], [-66.7, 10.7], [-67.1, 10.7]] },
  { name: "Falcón", polygon: [[-71.4, 10.3], [-68.7, 10.3], [-68.7, 12.6], [-71.4, 12.6]] },
  { name: "Guárico", polygon: [[-68.8, 7.8], [-65.1, 7.8], [-65.1, 10.5], [-68.8, 10.5]] },
  { name: "Lara", polygon: [[-70.8, 9.2], [-68.4, 9.2], [-68.4, 11.2], [-70.8, 11.2]] },
  { name: "La Guaira", polygon: [[-67.3, 10.4], [-66.4, 10.4], [-66.4, 10.8], [-67.3, 10.8]] },
  { name: "Mérida", polygon: [[-72.9, 7.2], [-70.5, 7.2], [-70.5, 9.0], [-72.9, 9.0]] },
  { name: "Miranda", polygon: [[-67.7, 9.8], [-65.8, 9.8], [-65.8, 10.9], [-67.7, 10.9]] },
  { name: "Monagas", polygon: [[-64.9, 8.0], [-62.3, 8.0], [-62.3, 10.5], [-64.9, 10.5]] },
  { name: "Nueva Esparta", polygon: [[-64.4, 10.8], [-63.6, 10.8], [-63.6, 11.3], [-64.4, 11.3]] },
  { name: "Portuguesa", polygon: [[-70.4, 8.1], [-68.5, 8.1], [-68.5, 9.8], [-70.4, 9.8]] },
  { name: "Sucre", polygon: [[-65.3, 9.8], [-61.7, 9.8], [-61.7, 11.2], [-65.3, 11.2]] },
  { name: "Táchira", polygon: [[-73.7, 7.0], [-71.2, 7.0], [-71.2, 8.6], [-73.7, 8.6]] },
  { name: "Trujillo", polygon: [[-71.9, 8.5], [-69.8, 8.5], [-69.8, 10.3], [-71.9, 10.3]] },
  { name: "Yaracuy", polygon: [[-70.1, 9.7], [-68.7, 9.7], [-68.7, 10.9], [-70.1, 10.9]] },
  { name: "Zulia", polygon: [[-73.7, 8.1], [-70.2, 8.1], [-70.2, 11.9], [-73.7, 11.9]] },
];

if (typeof module !== "undefined") {
  module.exports = { VENEZUELA_STATE_GEOFENCES };
}
