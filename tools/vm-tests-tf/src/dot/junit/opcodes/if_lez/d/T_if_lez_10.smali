.source "T_if_lez_10.java"
.class  public Ldot/junit/opcodes/if_lez/d/T_if_lez_10;
.super  Ljava/lang/Object;


.method public constructor <init>()V
.registers 1

       invoke-direct {v0}, Ljava/lang/Object;-><init>()V
       return-void
.end method

.method public run(I)Z
.registers 6

       if-lez v5, :Label8
       const/4 v5, 0
       return v5

:Label8
       nop
       return v5
.end method
