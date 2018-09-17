# Aggregator

Project for the preparation of parcel data before distribution of simulation with SimPLU3D program.

Installation
------

- [Install sbt](http://www.scala-sbt.org/0.13/docs/Installing-sbt-on-Linux.html)
- Addition of [IGN forge](https://forge-cogit.ign.fr/) certificate in [Java truststore](https://docs.microsoft.com/fr-fr/azure/java-add-certificate-ca-store)
- [Install IntellIJ](https://www.jetbrains.com/idea/) for code editing






Run the program
------

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
