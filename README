## ExifTool

ExifTool is a Java integration library for [Phil Harvey's (perl) ExifTool](http://www.sno.phy.queensu.ca/~phil/exiftool/), providing an easy way to read and write EXIF, IPTC, XMP and other metadata from image files. For example:

    ExifTool tool = new ExifTool();
    File imageFile = new File("/path/to/image.jpg");

    //Read Metadata
    Map<ExifTool.Tag,String> metadata = tool.getImageMeta(imageFile,
      ExifTool.Format.HUMAN_READABLE, ExifTool.Tag.values());
    String cameraModel = metadata.get(ExifTool.Tag.MODEL);

    ExifTool.Tag tag = ExifTool.Tag.IMAGE_HEIGHT;
    int imageWidth = tag.parseValue(metadata.get(tag));

    //Write Metadata
    Map<Object, Object> data = new HashMap<Object, Object>();
    data.put(ExifTool.MwgTag.KEYWORDS, new String[]{"portrait", "nature", "flower"});
    tool.writeMetadata(imageFile, data);


ExifTool was originally developed by [The Buzz Media](http://www.thebuzzmedia.com/software/exiftool-enhanced-java-integration-for-exiftool/ "Read more about the description and goals of the original project"). This repository is forked from the [original project](https://github.com/thebuzzmedia/exiftool) for use with [DF Studio](www.dfstudio.com "DF Studio is a cloud-based photo storage solution for professional and enterprise photography management and workflow, with features for organization, collaboration, storage and delivery of photographic assets.").
