CREATE TABLE fileAttachment(
    id SERIAL PRIMARY KEY,
    referenceType VARCHAR(100),
    referenceId BIGINT,
    fileName VARCHAR(256),
    originalName VARCHAR(256),
    folder VARCHAR(100),
    contentType VARCHAR(100),
    size BIGINT,
    uploadedBy VARCHAR(256),
    uploadedAt DATE,
    deletedDate DATE,
    presignedUrl VARCHAR(256),
    description VARCHAR(256)


);
