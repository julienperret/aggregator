# Aggregator

Project for the preparation of parcels data before distribution of simulation with SimPLU3D program.
The preparation includes :
- Block creation for calculation distribution ;
- Detection of "false parcels" in order to allow the simulation only on relevant parcels. "False parcels" are parcels that appear in cadastral dataset but that are not suitable for construction. It could be part of road extensions or private roads.

Globally, the data needs 2 datasets : BD Topo for roads, rivers, rails and buildings descriptions and PCI Vecteur for cadastre description.

Installation
------

- [Install sbt](http://www.scala-sbt.org/0.13/docs/Installing-sbt-on-Linux.html)
- Addition of [IGN forge](https://forge-cogit.ign.fr/) certificate in [Java truststore](https://docs.microsoft.com/fr-fr/azure/java-add-certificate-ca-store)
- [Install IntellIJ](https://www.jetbrains.com/idea/) for code editing


![Schema of the scripts of the project](https://github.com/julienperret/aggregator/blob/master/doc/schema.png)

Run the programs : UrbanBlock preparation
------
The aim is to produce groups of parcels that are suitable for SimPLU3D simulation. They require a cadastral parcel dataset and an ID_Group value is added

Two methods are proposed :
- **Plygonizer**: it uses linear datasets (roads, rivers, rails) to regroup the parels acccording to the faces of the topological map.
- **BuildUrbanBlocks**: it only requires parcels and regroup them according to the adjacency graph (on group by connex component).



Run the programs : Classification of false parcels
------
The following codes allow the classification of false parcels. A ground truth with a ''buildable'' attribute is required (value = 0 for non buildable parcels and 1 when buildable). The measures have to be calculated both on parcels to annotate and on ground truth parcels. Thus, the different processes can be launched on the ground truth with isGroundTruth boolean (that allows to keep the value of the attribute buildable between the different steps).

- **Aggregator** : agregate the different sources of BD Topo and PCI cadastral parcels when several departments are processed.
- **ComputeMeasures** : it computes values for the parcel and produces a **parcels_measures_idf_2** output file. The variable __folder__ that deteremines where files are read and exported has to be set. 4 layers are necessary (the methods to calculate non-existing attributes are present as comment in **Aggregator**) :
  - **parcel layer** (nammed parcels_idf) with MultiPolygon geometry. It requires attributes
    - _IDPAR_ : the id of the parcel
    - _WIDTH_ : the minimal dimension of the oriented bounding box
    - _HEIGHT_ : the maximal dimension of the oriented bounding box
    - _ELONGATION_ : the ratio WIDTH/HEIGHT
  - **building layer** : (nammed buildings_idf) with MultiPolygon geometry
  - **road network** : (nammed roads_surface_elongation_idf) with MultiPolygon geometry. It requires __POS\_SOL__ attribute that indicates if an object is over the ground (when __POS\_SOL__ >= 0)
  - **rail network** :  (nammed railway_surface_idf) with MultiPolygon geometry. It also requires __POS\_SOL__ attribute.
- **Classify** : it computes the type of the parcel according to 4 classes and requires  **parcels_measures_idf_2**  as input. :
  - __EMPTY__ : if the type cannote be determined
  - __ROAD__ : if the parcel is a road or a road extension
  - __RAILWAY__ : if the parcel is a railway
  - __BUILT__ : if the parcel is already built.
  The result is a csv file (**parcel_classes_dempster.csv**) that may be joined with the input shapefile
